/*
 * Copyright (c) 2026, Tyler Bochard
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
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.externalplugins;

import java.io.File;
import java.util.List;

/**
 * Optional seams for a host that can't load hub jars as-is. Desktop sets neither and
 * behaves exactly as upstream; the Android build installs both at boot:
 * <ul>
 *   <li>{@link #dexer} turns a downloaded (JVM bytecode) hub jar into something its
 *   class loader can define, before {@link PluginHubClassLoader} opens it.</li>
 *   <li>{@link #listener} is told how each install progresses, so the host can show it.</li>
 * </ul>
 */
public final class PluginHubHooks
{
	private PluginHubHooks()
	{
	}

	public enum Stage
	{
		MANIFEST,
		DOWNLOAD,
		VERIFY,
		DEX,
		LOAD,
		START,
	}

	public interface Dexer
	{
		/**
		 * @param jar the verified hub jar
		 * @return the file to hand the platform class loader
		 * @throws DexException when the jar can't be converted; the plugin is skipped
		 */
		File dex(String internalName, String displayName, String jarHash, File jar) throws DexException;
	}

	public interface Listener
	{
		default void downloadStarted(String internalName, String displayName, int jarSize)
		{
		}

		default void downloadProgress(String internalName, long bytes, int jarSize)
		{
		}

		default void downloadFinished(String internalName)
		{
		}

		/** The plugin's classes are loaded and, after startup, its plugins are started. */
		default void loaded(String internalName, String displayName, List<String> pluginNames)
		{
		}

		/** @param internalName null for a failure not tied to one plugin (the manifest) */
		default void failed(String internalName, String displayName, Stage stage, Throwable cause)
		{
		}
	}

	public static class DexException extends RuntimeException
	{
		public DexException(String message)
		{
			super(message);
		}

		public DexException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	public static volatile Dexer dexer;
	public static volatile Listener listener;

	static void notify(java.util.function.Consumer<Listener> event)
	{
		Listener l = listener;
		if (l == null)
		{
			return;
		}
		try
		{
			event.accept(l);
		}
		catch (Throwable t)
		{
			// A host UI bug must never break plugin loading.
		}
	}
}
