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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.util.VerificationException;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

@Slf4j
public class ExternalPluginClient
{
	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final HttpUrl apiBase;
	private final HttpUrl pluginHubBase;
	private volatile String fallbackVersion;

	@Inject
	private ExternalPluginClient(OkHttpClient okHttpClient,
		Gson gson,
		@Named("runelite.api.base") HttpUrl apiBase,
		@Named("runelite.pluginhub.url") HttpUrl pluginHubBase
	)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.apiBase = apiBase;
		this.pluginHubBase = pluginHubBase;
	}

	public PluginHubManifest.ManifestLite downloadManifestLite() throws IOException, VerificationException
	{
		return downloadManifest("lite", PluginHubManifest.ManifestLite.class);
	}

	public PluginHubManifest.ManifestFull downloadManifestFull() throws IOException, VerificationException
	{
		return downloadManifest("full", PluginHubManifest.ManifestFull.class);
	}

	private <T> T downloadManifest(String name, Class<T> clazz) throws IOException, VerificationException
	{
		String version = fallbackVersion != null ? fallbackVersion : RuneLiteProperties.getPluginHubVersion();
		try
		{
			return downloadManifest(version, name, clazz);
		}
		catch (ManifestNotFoundException e)
		{
			String fallback = resolveFallbackVersion(name);
			if (fallback == null || fallback.equals(version))
			{
				throw e;
			}
			log.info("Plugin hub has no {} manifest for {}, falling back to {}", name, version, fallback);
			fallbackVersion = fallback;
			return downloadManifest(fallback, name, clazz);
		}
	}

	/**
	 * A fork hub hosted on raw.githubusercontent.com keeps exactly one manifest version,
	 * which lags behind the client after a RuneLite bump until the hub is rebuilt. List the
	 * repo's manifest/ directory through the GitHub contents API and use whatever version
	 * is there. Returns null for any other host (upstream hub) or on failure.
	 */
	private String resolveFallbackVersion(String name)
	{
		if (!"raw.githubusercontent.com".equals(pluginHubBase.host()) || pluginHubBase.pathSize() < 3)
		{
			return null;
		}

		// raw URL layout: /<owner>/<repo>/<branch>[/<subdir>...]
		List<String> seg = pluginHubBase.pathSegments();
		HttpUrl.Builder url = HttpUrl.get("https://api.github.com").newBuilder()
			.addPathSegment("repos")
			.addPathSegment(seg.get(0))
			.addPathSegment(seg.get(1))
			.addPathSegment("contents");
		for (String s : seg.subList(3, seg.size()))
		{
			if (!s.isEmpty())
			{
				url.addPathSegment(s);
			}
		}
		url.addPathSegment("manifest")
			.addQueryParameter("ref", seg.get(2));
		String suffix = "_" + name + ".js";
		try (Response res = okHttpClient.newCall(new Request.Builder().url(url.build()).build()).execute())
		{
			if (res.code() != 200)
			{
				log.warn("Unable to list plugin hub manifests: {}", res.code());
				return null;
			}

			// CHECKSTYLE:OFF
			List<Map<String, Object>> entries = gson.fromJson(res.body().string(), new TypeToken<List<Map<String, Object>>>(){}.getType());
			// CHECKSTYLE:ON
			for (Map<String, Object> entry : entries)
			{
				Object fileName = entry.get("name");
				if (fileName instanceof String && ((String) fileName).endsWith(suffix))
				{
					String f = (String) fileName;
					return f.substring(0, f.length() - suffix.length());
				}
			}
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Unable to list plugin hub manifests", e);
		}
		return null;
	}

	private static class ManifestNotFoundException extends IOException
	{
		ManifestNotFoundException(String message)
		{
			super(message);
		}
	}

	private <T> T downloadManifest(String version, String name, Class<T> clazz) throws IOException, VerificationException
	{
		HttpUrl manifest = pluginHubBase
			.newBuilder()
			.addPathSegment("manifest")
			.addPathSegment(version + "_" + name + ".js")
			.build();
		try (Response res = okHttpClient.newCall(new Request.Builder().url(manifest).build()).execute())
		{
			if (res.code() == 404)
			{
				throw new ManifestNotFoundException("No " + name + " manifest for " + version);
			}
			if (res.code() != 200)
			{
				throw new IOException("Non-OK response code: " + res.code());
			}

			BufferedSource src = res.body().source();

			byte[] signature = new byte[src.readInt()];
			src.readFully(signature);

			byte[] data = src.readByteArray();
			Signature s = Signature.getInstance("SHA256withRSA");
			s.initVerify(loadCertificate());
			s.update(data);

			if (!s.verify(signature))
			{
				throw new VerificationException("Unable to verify external plugin manifest");
			}

			return gson.fromJson(new String(data, StandardCharsets.UTF_8), clazz);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e)
		{
			throw new VerificationException(e);
		}
	}

	public BufferedImage downloadIcon(PluginHubManifest.DisplayData plugin) throws IOException
	{
		if (plugin.getIconHash() == null)
		{
			return null;
		}

		HttpUrl url = pluginHubBase
			.newBuilder()
			.addPathSegment("icon")
			.addPathSegment(plugin.getInternalName() + "_" + plugin.getIconHash() + ".png")
			.build();

		try (Response res = okHttpClient.newCall(new Request.Builder().url(url).build()).execute())
		{
			byte[] bytes = res.body().bytes();
			// We don't stream so the lock doesn't block the edt trying to load something at the same time
			synchronized (ImageIO.class)
			{
				return ImageIO.read(new ByteArrayInputStream(bytes));
			}
		}
	}

	HttpUrl getJarURL(PluginHubManifest.JarData plugin)
	{
		return pluginHubBase
			.newBuilder()
			.addPathSegment("jar")
			.addPathSegment(plugin.getInternalName() + "_" + plugin.getJarHash() + ".jar")
			.build();
	}

	/**
	 * Load the trust anchor used to verify hub manifest signatures. The bundled cert is
	 * upstream RuneLite's 4096-bit RSA cert that signs the official hub. Forks running
	 * their own hub override the resource name via {@code -Drunelite.pluginhub.cert=<name>}
	 * — runelite-mp does this at Android boot to swap in {@code externalplugins-android.crt},
	 * the 2048-bit cert generated by {@code pluginhub-lib :generateKeys}. Desktop never sets
	 * the property and stays on upstream.
	 */
	private static Certificate loadCertificate()
	{
		String resource = System.getProperty("runelite.pluginhub.cert", "externalplugins.crt");
		try (InputStream in = ExternalPluginClient.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new IOException("Hub cert resource not found: " + resource);
			}
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			return certFactory.generateCertificate(in);
		}
		catch (CertificateException | IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	void submitPlugins(List<String> plugins)
	{
		if (plugins.isEmpty())
		{
			return;
		}

		HttpUrl url = apiBase.newBuilder()
			.addPathSegment("pluginhub")
			.build();

		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(RuneLiteAPI.JSON, gson.toJson(plugins)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Error submitting plugins", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				log.debug("Submitted plugin list");
				response.close();
			}
		});
	}

	public Map<String, Integer> getPluginCounts() throws IOException
	{
		HttpUrl url = apiBase
			.newBuilder()
			.addPathSegments("pluginhub")
			.build();
		try (Response res = okHttpClient.newCall(new Request.Builder().url(url).build()).execute())
		{
			if (res.code() != 200)
			{
				throw new IOException("Non-OK response code: " + res.code());
			}

			// CHECKSTYLE:OFF
			return gson.fromJson(res.body().string(), new TypeToken<Map<String, Integer>>(){}.getType());
			// CHECKSTYLE:ON
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException(ex);
		}
	}
}
