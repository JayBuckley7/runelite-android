/*
 * Copyright (c) 2019 Abex
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

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.util.ReflectUtil;

class PluginHubClassLoader extends URLClassLoader implements ReflectUtil.PrivateLookupableClassLoader
{
	/** Dalvik doesn't run JVM bytecode, so URLClassLoader can't actually load .class files from
	 *  a hub jar on Android. The pluginhub-lib pipeline dexes each jar (classes.dex + resources
	 *  at the root), and on Android we delegate findClass to a DexClassLoader pointed at the
	 *  same file. URLClassLoader still owns resource lookup so the stub-read in the constructor
	 *  and any later getResource calls keep working through the same code path. */
	private static final boolean IS_ANDROID = "Dalvik".equals(System.getProperty("java.vm.name"));

	/** On Android the plugin classes are *defined* by the inner DexClassLoader (findClass
	 *  delegates to it), so {@code plugin.getClass().getClassLoader()} is that DexClassLoader,
	 *  NOT this wrapper. That made {@code cl instanceof PluginHubClassLoader} fail everywhere
	 *  external-plugin identity is recovered (ExternalPluginManager.getJarData/getStub),
	 *  returning null for every external — which broke the "already loaded" dedup and caused
	 *  refreshPlugins to instantiate duplicates, whose same-name conflict cascade then
	 *  self-disabled the plugin on enable so nothing ever started. Map each inner loader back
	 *  to its wrapper so {@link #forClassLoader} can recover it. Entries are only added on a
	 *  real (infrequent) load, so the strong map is effectively bounded. */
	private static final Map<ClassLoader, PluginHubClassLoader> DEX_OWNERS =
		Collections.synchronizedMap(new IdentityHashMap<>());

	/** Resolve the owning PluginHubClassLoader for a plugin class's loader: directly on
	 *  desktop (URLClassLoader is the defining loader), or via the DEX_OWNERS map on Android
	 *  (where the inner DexClassLoader is the defining loader). */
	static PluginHubClassLoader forClassLoader(ClassLoader cl)
	{
		if (cl instanceof PluginHubClassLoader)
		{
			return (PluginHubClassLoader) cl;
		}
		return cl == null ? null : DEX_OWNERS.get(cl);
	}

	@Getter
	private final PluginHubManifest.JarData jarData;

	@Getter
	private final PluginHubManifest.Stub stub;

	@Getter
	@Setter
	private MethodHandles.Lookup lookup;

	private final ClassLoader dexClassLoader;

	PluginHubClassLoader(PluginHubManifest.JarData jarData, URL[] urls, Gson gson) throws IOException
	{
		super(urls, PluginHubClassLoader.class.getClassLoader());
		this.jarData = jarData;
		try (InputStream is = getResourceAsStream("runelite_plugin.json"))
		{
			this.stub = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), PluginHubManifest.Stub.class);
		}
		this.dexClassLoader = IS_ANDROID ? createDexClassLoader(dexed(jarData)) : null;
		if (this.dexClassLoader != null)
		{
			// Plugin classes are defined by dexClassLoader, so identity recovery off a plugin
			// Class lands here, not on `this`. Register so forClassLoader() can map back.
			DEX_OWNERS.put(this.dexClassLoader, this);
		}
		ReflectUtil.installLookupHelper(this);
	}

	/** The upstream hub serves JVM bytecode; the host's {@link PluginHubHooks#dexer} converts
	 *  it. Resources (and the stub above) keep coming from the original jar via
	 *  URLClassLoader, so the converted file only has to carry the dex. Without a dexer the
	 *  jar is expected to be pre-dexed already. */
	private static File dexed(PluginHubManifest.JarData jarData)
	{
		File jar = jarData.getJarFile();
		PluginHubHooks.Dexer dexer = PluginHubHooks.dexer;
		return dexer == null ? jar
			: dexer.dex(jarData.getInternalName(), jarData.getDisplayName(), jarData.getJarHash(), jar);
	}

	/** Reflective so runelite-client still compiles on a plain JDK (no dalvik.system on
	 *  the desktop classpath). The constructor signature has been stable since API 14:
	 *  (dexPath, optimizedDirectory, librarySearchPath, parent). optimizedDirectory has been
	 *  ignored since Android 8 but is still non-null-required on older devices. Files
	 *  downloaded by ExternalPluginManager land RW by default; Android API 29+ throws
	 *  SecurityException("Writable dex file ... is not allowed.") so flip to read-only
	 *  before handing the path to the class loader. */
	private static ClassLoader createDexClassLoader(File jarFile)
	{
		try
		{
			if (jarFile.canWrite() && !jarFile.setReadOnly())
			{
				throw new IOException("Could not chmod " + jarFile + " to read-only");
			}
			Class<?> dcl = Class.forName("dalvik.system.DexClassLoader");
			return (ClassLoader) dcl
				.getConstructor(String.class, String.class, String.class, ClassLoader.class)
				.newInstance(
					jarFile.getAbsolutePath(),
					jarFile.getParentFile().getAbsolutePath(),
					null,
					PluginHubClassLoader.class.getClassLoader());
		}
		catch (ReflectiveOperationException | IOException e)
		{
			throw new RuntimeException("Failed to create DexClassLoader for " + jarFile, e);
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException
	{
		if (dexClassLoader != null)
		{
			return dexClassLoader.loadClass(name);
		}
		return super.findClass(name);
	}

	@Override
	public Class<?> defineClass0(String name, byte[] b, int off, int len) throws ClassFormatError
	{
		return super.defineClass(name, b, off, len);
	}
}
