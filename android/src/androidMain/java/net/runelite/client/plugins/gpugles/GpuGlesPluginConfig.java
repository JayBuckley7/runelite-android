package net.runelite.client.plugins.gpugles;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(GpuGlesPluginConfig.GROUP)
public interface GpuGlesPluginConfig extends Config
{
	String GROUP = "gpugles";

	int MAX_DISTANCE = 184;
	int MAX_FOG_DEPTH = 100;

	@ConfigItem(
		keyName = "vulkanRenderer",
		name = "Vulkan renderer (experimental)",
		description = "Render through Vulkan instead of OpenGL ES. Falls back to OpenGL ES automatically " +
			"if the device can't start Vulkan.",
		position = 0)
	default boolean vulkanRenderer() { return false; }

	@Range(max = MAX_DISTANCE)
	@ConfigItem(keyName = "drawDistance", name = "Draw distance", description = "Draw distance in tiles.", position = 1)
	default int drawDistance() { return 50; }

	@Range(max = MAX_FOG_DEPTH)
	@ConfigItem(keyName = "fogDepth", name = "Fog depth", description = "Distance from scene edge fog starts.", position = 2)
	default int fogDepth() { return 0; }

	@Range(max = 5)
	@ConfigItem(keyName = "expandedMapLoadingChunks", name = "Expanded map loading", description = "Extra 8-tile chunks to keep loaded.", position = 3)
	default int expandedMapLoadingZones() { return 3; }

	@ConfigItem(keyName = "smoothBanding", name = "Smooth banding", description = "Remove color banding artifacts.", position = 4)
	default boolean smoothBanding() { return true; }

	@ConfigItem(keyName = "hideUnrelatedMaps", name = "Hide unrelated maps", description = "Filter out invisible region areas.", position = 10)
	default boolean hideUnrelatedMaps() { return true; }

	@ConfigItem(keyName = "brightTextures", name = "Bright textures", description = "Legacy bright texture lighting.", position = 11)
	default boolean brightTextures() { return false; }

	@Range(max = 100)
	@ConfigItem(keyName = "colorBlindIntensity", name = "Color blind intensity", description = "0-100", position = 12)
	default int colorBlindIntensity() { return 100; }

	@Range(max = 16)
	@ConfigItem(keyName = "anisotropicFilteringLevel", name = "Anisotropic filtering", description = "0=off, higher=better quality.", position = 13)
	default int anisotropicFilteringLevel() { return 1; }

	@ConfigItem(keyName = "removeVertexSnapping", name = "Remove vertex snapping", description = "Smooth vertex animation.", position = 5)
	default boolean removeVertexSnapping() { return true; }

	@ConfigItem(keyName = "unlockFps", name = "Unlock FPS", description = "Remove the 50 FPS camera cap.", position = 6)
	default boolean unlockFps() { return true; }

	@Range(min = 1, max = 999)
	@ConfigItem(keyName = "fpsTarget", name = "FPS target", description = "Target FPS when FPS is unlocked.", position = 7)
	default int fpsTarget() { return 60; }

	@Range(max = 100)
	@ConfigItem(keyName = "brightness", name = "Brightness", description = "Scene brightness.", position = 8)
	default int brightness() { return 50; }

	@Range(min = 25, max = 100)
	@ConfigItem(
		keyName = "resolutionScale",
		name = "Resolution %",
		description = "Render at a fraction of native resolution (Android upscales the result). " +
			"Lower = much higher FPS on mobile, softer image. 100 = native (per-pixel sharp).",
		position = 14)
	default int resolutionScale() { return 75; }

	@Range(max = 4)
	@ConfigItem(
		keyName = "numThreads",
		name = "Render threads",
		description = "Worker threads the engine may use to build model geometry each frame. " +
			"Helps most in crowded areas on multi-core devices; each thread costs a few MB. " +
			"0 = everything on the client thread (the old behaviour).",
		position = 16)
	default int numThreads() { return 2; }

	enum MsaaSamples
	{
		OFF(0), TWO(2), FOUR(4);
		final int samples;
		MsaaSamples(int s) { this.samples = s; }
	}

	@ConfigItem(
		keyName = "msaaSamples",
		name = "MSAA",
		description = "Hardware anti-aliasing samples. Heavy on mobile — leave off unless you can spare the GPU. " +
			"Takes effect after restarting the plugin (toggle GPU off → on).",
		position = 15)
	default MsaaSamples msaaSamples() { return MsaaSamples.OFF; }

}
