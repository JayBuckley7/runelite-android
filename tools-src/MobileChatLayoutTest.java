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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MobileChatLayoutTest
{
	private static final class Box
	{
		final Widget widget = mock(Widget.class);
		final int[] position = {0, 0, 0, 0};
		Box(int height)
		{
			when(widget.getHeight()).thenReturn(height);
			when(widget.getCanvasLocation()).thenReturn(new Point(0, 0));
			when(widget.getOriginalX()).thenAnswer(call -> position[0]);
			when(widget.getOriginalY()).thenAnswer(call -> position[1]);
			when(widget.getXPositionMode()).thenAnswer(call -> position[2]);
			when(widget.getYPositionMode()).thenAnswer(call -> position[3]);
			when(widget.setOriginalX(anyInt())).thenAnswer(call -> {
				position[0] = call.getArgument(0);
				return widget;
			});
			when(widget.setOriginalY(anyInt())).thenAnswer(call -> {
				position[1] = call.getArgument(0);
				return widget;
			});
			when(widget.setXPositionMode(anyInt())).thenAnswer(call -> {
				position[2] = call.getArgument(0);
				return widget;
			});
			when(widget.setYPositionMode(anyInt())).thenAnswer(call -> {
				position[3] = call.getArgument(0);
				return widget;
			});
		}
	}

	@Test
	public void nativeOpenCloseKeepsStonesAtTopAndRestores()
	{
		Box root = new Box(165), stones = new Box(23), area = new Box(142);
		stones.position[3] = 2;
		MobileChatLayout layout = new MobileChatLayout();
		layout.apply(root.widget, stones.widget, area.widget, null, true, 4);
		assertEquals(0, stones.position[1]);
		assertEquals(0, stones.position[3]);
		assertEquals(23, area.position[1]);
		when(area.widget.isHidden()).thenReturn(true);
		layout.apply(root.widget, stones.widget, area.widget, null, true, 4);
		verify(stones.widget, times(1)).revalidate();
		verify(area.widget, times(1)).revalidate();
		verify(area.widget, never()).setHidden(anyBoolean());
		layout.restore();
		assertEquals(2, stones.position[3]);
		assertEquals(0, area.position[1]);
	}

	@Test
	public void splitPrivateChatFollowsExpandedAndCollapsedBox()
	{
		Box root = new Box(165), stones = new Box(23), area = new Box(142), pm = new Box(1000);
		when(root.widget.getCanvasLocation()).thenReturn(new Point(50, 10));
		Box older = new Box(12), newer = new Box(12);
		when(older.widget.getRelativeY()).thenReturn(900);
		when(newer.widget.getRelativeY()).thenReturn(920);
		when(pm.widget.getChildren()).thenReturn(new Widget[] {older.widget, newer.widget});
		MobileChatLayout layout = new MobileChatLayout();
		layout.apply(root.widget, stones.widget, area.widget, pm.widget, true, 4);
		assertEquals(50, pm.position[0]);
		assertEquals(10 + 165 + 4 - 900, pm.position[1]);
		when(area.widget.isHidden()).thenReturn(true);
		layout.apply(root.widget, stones.widget, area.widget, pm.widget, true, 4);
		assertEquals(10 + 23 + 4 - 900, pm.position[1]);
		layout.apply(root.widget, stones.widget, area.widget, pm.widget, false, 4);
		assertEquals(0, pm.position[0]);
		assertEquals(0, pm.position[1]);
	}

	@Test
	public void insufficientSpaceKeepsPrivateMessagesInTheirNativePosition()
	{
		Box root = new Box(165), stones = new Box(23), area = new Box(142), pm = new Box(1000), line = new Box(12);
		when(root.widget.getCanvasLocation()).thenReturn(new Point(0, 950));
		when(line.widget.getRelativeY()).thenReturn(900);
		when(pm.widget.getChildren()).thenReturn(new Widget[] {line.widget});
		MobileChatLayout layout = new MobileChatLayout();
		layout.apply(root.widget, stones.widget, area.widget, pm.widget, true, 4);
		assertEquals(0, pm.position[1]);
		verify(pm.widget, never()).revalidate();
	}

	@Test
	public void nativeRelayoutBecomesTheNewRestorePoint()
	{
		Box root = new Box(165), stones = new Box(23), area = new Box(142);
		stones.position[3] = 2;
		MobileChatLayout layout = new MobileChatLayout();
		layout.apply(root.widget, stones.widget, area.widget, null, true, 4);
		stones.position[1] = 7;
		stones.position[3] = 2;
		layout.apply(root.widget, stones.widget, area.widget, null, true, 4);
		layout.restore();
		assertEquals(7, stones.position[1]);
		assertEquals(2, stones.position[3]);
	}

	@Test
	public void disablingDoesNotUndoAnotherOwnersLaterEdit()
	{
		Box root = new Box(165), stones = new Box(23), area = new Box(142);
		MobileChatLayout layout = new MobileChatLayout();
		layout.apply(root.widget, stones.widget, area.widget, null, true, 4);
		area.position[1] = 80;
		layout.restore();
		assertEquals(80, area.position[1]);
	}

	@Test
	public void recreatedWidgetsDoNotKeepOldPlacements()
	{
		Box root = new Box(165), oldStones = new Box(23), oldArea = new Box(142), newStones = new Box(23),
			newArea = new Box(142);
		oldStones.position[3] = 2;
		newStones.position[3] = 2;
		MobileChatLayout layout = new MobileChatLayout();
		layout.apply(root.widget, oldStones.widget, oldArea.widget, null, true, 4);
		layout.apply(root.widget, newStones.widget, newArea.widget, null, true, 4);
		assertEquals(2, oldStones.position[3]);
		assertEquals(0, oldArea.position[1]);
		assertEquals(23, newArea.position[1]);
		layout.restore();
		assertEquals(2, newStones.position[3]);
		assertEquals(0, newArea.position[1]);
	}

	@Test
	public void checkboxDefaultsOffAndRestoresOnDisable()
	{
		assertEquals(false, new MobileConfig() {}.invertChat());
		Client client = mock(Client.class);
		Box root = new Box(165), stones = new Box(23), area = new Box(142);
		stones.position[3] = 2;
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isResized()).thenReturn(true);
		when(client.getWidget(InterfaceID.Chatbox.UNIVERSE)).thenReturn(root.widget);
		when(client.getWidget(InterfaceID.Chatbox.CONTROLS)).thenReturn(stones.widget);
		when(client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(area.widget);
		MobileChatLayout layout = new MobileChatLayout();
		layout.update(client, false, 100);
		assertEquals(2, stones.position[3]);
		layout.update(client, true, 100);
		assertEquals(23, area.position[1]);
		layout.update(client, false, 100);
		assertEquals(2, stones.position[3]);
		assertEquals(0, area.position[1]);
	}

	@Test
	public void fixedModeLogoutAndMissingWidgetsRestoreNativeLayout()
	{
		Client client = mock(Client.class);
		Box root = new Box(165), stones = new Box(23), area = new Box(142);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isResized()).thenReturn(true);
		when(client.getWidget(InterfaceID.Chatbox.UNIVERSE)).thenReturn(root.widget);
		when(client.getWidget(InterfaceID.Chatbox.CONTROLS)).thenReturn(stones.widget);
		when(client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(area.widget);
		MobileChatLayout layout = new MobileChatLayout();
		layout.update(client, true, 100);
		when(client.isResized()).thenReturn(false);
		layout.update(client, true, 100);
		assertEquals(0, area.position[1]);
		when(client.isResized()).thenReturn(true);
		layout.update(client, true, 100);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		layout.update(client, true, 100);
		assertEquals(0, area.position[1]);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		layout.update(client, true, 100);
		when(client.getWidget(InterfaceID.Chatbox.UNIVERSE)).thenReturn(null);
		layout.update(client, true, 100);
		assertEquals(0, area.position[1]);
	}

	@Test
	public void splitMessagesFollowEnlargedChatWhenOpenAndCollapsed()
	{
		Box root = new Box(165), stones = new Box(23), area = new Box(142), pm = new Box(1000);
		Box line = new Box(12);
		when(root.widget.getCanvasLocation()).thenReturn(new Point(50, 100));
		when(line.widget.getRelativeY()).thenReturn(900);
		when(pm.widget.getChildren()).thenReturn(new Widget[] {line.widget});
		InventoryScaleGeometry scale = new InventoryScaleGeometry(new Rectangle(50, 100, 400, 165), 150, 1000, 1000);
		MobileChatLayout layout = new MobileChatLayout();

		layout.apply(root.widget, stones.widget, area.widget, pm.widget, true, 4, scale);
		assertEquals(scale.destination.x, pm.position[0]);
		assertEquals(scale.destination.y + scale.destination.height + 4 - 900, pm.position[1]);

		when(area.widget.isHidden()).thenReturn(true);
		layout.apply(root.widget, stones.widget, area.widget, pm.widget, true, 4, scale);
		assertEquals(scale.destination.y + 23 * scale.destination.height / 165 + 4 - 900, pm.position[1]);
		layout.restore();
		assertEquals(0, pm.position[0]);
		assertEquals(0, pm.position[1]);
	}
}
