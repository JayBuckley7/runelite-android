/*
 * Copyright (c) 2026, Jay Buckley
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.mobile;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Changes only widget placement. Visibility, contents, events and scrolling stay native. */
final class MobileChatLayout
{
	private final Map<Widget, Placement> owned = new IdentityHashMap<>();

	void update(Client client, boolean enabled, int chatScale)
	{
		if (!enabled || client.getGameState() != GameState.LOGGED_IN || !client.isResized())
		{
			restore();
			return;
		}

		Widget root = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		Widget stones = client.getWidget(InterfaceID.Chatbox.CONTROLS);
		Widget area = client.getWidget(InterfaceID.Chatbox.CHATAREA);
		if (root == null || stones == null || area == null || root.isHidden() || stones.isHidden()
			|| stones.getHeight() <= 0)
		{
			restore();
			return;
		}

		apply(root, stones, area, client.getWidget(InterfaceID.PmChat.CONTAINER), true, 4, scaledChat(client, chatScale));
	}

	private static InventoryScaleGeometry scaledChat(Client client, int percent)
	{
		if (percent == 100 || !client.isGpu())
		{
			return null;
		}
		Widget container = client.getWidget(InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER);
		if (container == null || container.isHidden())
		{
			container = client.getWidget(InterfaceID.ToplevelPreEoc.CHAT_CONTAINER);
		}
		BufferProvider buffer = client.getBufferProvider();
		if (container == null || container.isHidden() || buffer == null)
		{
			return null;
		}
		Rectangle bounds = container.getBounds();
		if (bounds.width <= 0 || bounds.height <= 0
			|| !new Rectangle(0, 0, buffer.getWidth(), buffer.getHeight()).contains(bounds))
		{
			return null;
		}
		return new InventoryScaleGeometry(bounds, percent, buffer.getWidth(), buffer.getHeight());
	}

	void apply(Widget root, Widget stones, Widget area, Widget privateChat, boolean followPrivate, int gap)
	{
		apply(root, stones, area, privateChat, followPrivate, gap, null);
	}

	void apply(Widget root, Widget stones, Widget area, Widget privateChat, boolean followPrivate, int gap,
		InventoryScaleGeometry scaledChat)
	{
		Set<Widget> keep = Collections.newSetFromMap(new IdentityHashMap<>());
		moveY(stones, 0);
		keep.add(stones);
		moveY(area, stones.getHeight());
		keep.add(area);

		if (followPrivate && privateChat != null && !privateChat.isHidden())
		{
			int firstLine = firstVisibleLine(privateChat.getChildren());
			Point anchor = root.getCanvasLocation();
			Widget parent = privateChat.getParent();
			Point parentLocation = parent == null ? new Point(0, 0) : parent.getCanvasLocation();
			if (firstLine != Integer.MAX_VALUE && anchor != null && parentLocation != null)
			{
				int boxHeight = stones.getHeight() + (area.isHidden() ? 0 : area.getHeight());
				int left = anchor.getX();
				int bottom = anchor.getY() + boxHeight;
				if (scaledChat != null)
				{
					Rectangle source = scaledChat.source;
					Rectangle destination = scaledChat.destination;
					left = destination.x + (left - source.x) * destination.width / source.width;
					bottom = destination.y + (bottom - source.y) * destination.height / source.height;
				}
				int x = left - parentLocation.getX();
				int top = bottom + gap - parentLocation.getY();
				int available = parent == null ? privateChat.getHeight() : parent.getHeight();
				int blockHeight = lastVisibleBottom(privateChat.getChildren()) - firstLine;
				if (top >= 0 && top + blockHeight <= available)
				{
					move(privateChat, new Position(x, top - firstLine, 0, 0));
					keep.add(privateChat);
				}
			}
		}
		Iterator<Map.Entry<Widget, Placement>> iterator = owned.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Widget, Placement> entry = iterator.next();
			if (!keep.contains(entry.getKey()))
			{
				entry.getValue().restore(entry.getKey());
				iterator.remove();
			}
		}
	}

	private void moveY(Widget widget, int y)
	{
		move(widget, new Position(widget.getOriginalX(), y, widget.getXPositionMode(), 0));
	}

	private void move(Widget widget, Position desired)
	{
		Position current = Position.read(widget);
		Placement placement = owned.get(widget);
		if (placement == null)
		{
			placement = new Placement(current);
			owned.put(widget, placement);
		}
		else if (!current.equals(placement.applied))
		{
			placement.original = current;
		}
		if (!current.equals(desired))
		{
			desired.write(widget);
		}
		placement.applied = desired;
	}

	static int firstVisibleLine(Widget[] children)
	{
		int y = Integer.MAX_VALUE;
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null && !child.isSelfHidden() && child.getHeight() > 0)
				{
					y = Math.min(y, child.getRelativeY());
				}
			}
		}
		return y;
	}

	private static int lastVisibleBottom(Widget[] children)
	{
		int bottom = 0;
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null && !child.isSelfHidden() && child.getHeight() > 0)
				{
					bottom = Math.max(bottom, child.getRelativeY() + child.getHeight());
				}
			}
		}
		return bottom;
	}

	void restore()
	{
		owned.forEach((widget, placement) -> placement.restore(widget));
		owned.clear();
	}

	private static final class Placement
	{
		private Position original;
		private Position applied;

		private Placement(Position original)
		{
			this.original = original;
		}

		private void restore(Widget widget)
		{
			// Do not undo a later position written by the game or another plugin.
			if (Position.read(widget).equals(applied))
			{
				original.write(widget);
			}
		}
	}

	private static final class Position
	{
		private final int x;
		private final int y;
		private final int xMode;
		private final int yMode;

		private Position(int x, int y, int xMode, int yMode)
		{
			this.x = x;
			this.y = y;
			this.xMode = xMode;
			this.yMode = yMode;
		}

		private static Position read(Widget w)
		{
			return new Position(w.getOriginalX(), w.getOriginalY(), w.getXPositionMode(), w.getYPositionMode());
		}

		private void write(Widget w)
		{
			w.setOriginalX(x);
			w.setOriginalY(y);
			w.setXPositionMode(xMode);
			w.setYPositionMode(yMode);
			w.revalidate();
		}

		@Override
		public boolean equals(Object object)
		{
			if (!(object instanceof Position))
			{
				return false;
			}
			Position other = (Position) object;
			return x == other.x && y == other.y && xMode == other.xMode && yMode == other.yMode;
		}

		@Override
		public int hashCode()
		{
			return java.util.Objects.hash(x, y, xMode, yMode);
		}
	}
}
