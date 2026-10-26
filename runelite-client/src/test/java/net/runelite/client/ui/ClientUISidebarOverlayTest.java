package net.runelite.client.ui;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.JTabbedPane;
import net.runelite.api.Client;
import net.runelite.api.Point;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class ClientUISidebarOverlayTest
{
	private ClientUI ui;
	private Graphics2D graphics;
	private Rectangle hitbox;

	@Before
	public void setUp() throws Exception
	{
		ui = mock(ClientUI.class, CALLS_REAL_METHODS);
		graphics = mock(Graphics2D.class);
		hitbox = new Rectangle(10, 10, 20, 20);
		set("sidebarButtonPosition", hitbox);
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = ClientUI.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(ui, value);
	}

	@Test
	public void drawingBeforeInitializationDoesNotCrash() throws Exception
	{
		ui.paintOverlays(graphics);
		assertTrue(hitbox.isEmpty());
		verifyNoInteractions(graphics);
	}

	@Test
	public void partiallyInitializedSidebarDoesNotCrash() throws Exception
	{
		set("sidebar", new JTabbedPane());
		set("sidebarOpenIcon", new BufferedImage(10, 12, BufferedImage.TYPE_INT_ARGB));
		ui.paintOverlays(graphics);
		assertTrue(hitbox.isEmpty());
		verifyNoInteractions(graphics);
	}

	@Test
	public void initializedToggleDrawsOnlyForDesktopChrome() throws Exception
	{
		Canvas component = mock(Canvas.class, withSettings().extraInterfaces(Client.class));
		Client client = (Client) component;
		when(client.getRealDimensions()).thenReturn(new Dimension(800, 600));
		when(client.getMouseCanvasPosition()).thenReturn(new Point(790, 10));
		set("client", component);
		JTabbedPane sidebar = new JTabbedPane();
		sidebar.setVisible(false);
		set("sidebar", sidebar);
		BufferedImage open = new BufferedImage(10, 12, BufferedImage.TYPE_INT_ARGB);
		BufferedImage close = new BufferedImage(10, 12, BufferedImage.TYPE_INT_ARGB);
		set("sidebarOpenIcon", open);
		set("sidebarCloseIcon", close);
		ui.paintOverlays(graphics);
		if (Boolean.getBoolean("runelite.headlessChrome"))
		{
			assertTrue(hitbox.isEmpty());
			verifyNoInteractions(graphics, client);
		}
		else
		{
			verify(graphics).drawImage(open, 785, 5, null);
			assertEquals(new Rectangle(785, 5, 10, 12), hitbox);
			sidebar.setVisible(true);
			ui.paintOverlays(graphics);
			verify(graphics).drawImage(close, 785, 5, null);
		}
	}
}
