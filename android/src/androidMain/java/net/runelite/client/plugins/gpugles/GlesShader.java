package net.runelite.client.plugins.gpugles;

import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glDetachShader;
import static android.opengl.GLES20.glGetProgramInfoLog;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glValidateProgram;

import android.opengl.GLES20;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-stage shader program builder. Mirrors net.runelite.client.plugins.gpu.Shader:
 * register vertex + fragment shaders by source filename, then compile() loads,
 * compiles, attaches, links, and validates them — returning the GL program ID.
 * Errors throw ShaderException so the plugin can disable itself rather than
 * silently render black.
 */
final class GlesShader
{
	private static final String TAG = "GlesShader";

	static final class Unit
	{
		final int type;
		final String filename;
		Unit(int type, String filename) { this.type = type; this.filename = filename; }
	}

	final List<Unit> units = new ArrayList<>();

	GlesShader add(int type, String name)
	{
		units.add(new Unit(type, name));
		return this;
	}

	int compile(GlesTemplate template) throws ShaderException
	{
		if (Gfx.vulkan)
		{
			return compileVulkan(template);
		}

		int program = glCreateProgram();
		int[] shaders = new int[units.size()];
		int i = 0;
		boolean ok = false;
		try
		{
			while (i < shaders.length)
			{
				Unit unit = units.get(i);
				int shader = glCreateShader(unit.type);
				if (shader == 0)
				{
					throw new ShaderException("glCreateShader returned 0 for type " + unit.type);
				}

				String source = template.load(unit.filename);
				glShaderSource(shader, source);
				glCompileShader(shader);

				int[] status = new int[1];
				glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
				if (status[0] != GLES20.GL_TRUE)
				{
					String err = glGetShaderInfoLog(shader);
					glDeleteShader(shader);
					logShaderSource(unit.filename, source);
					throw new ShaderException(unit.filename + ": " + err);
				}
				glAttachShader(program, shader);
				shaders[i++] = shader;
			}

			glLinkProgram(program);

			int[] linkStatus = new int[1];
			glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] == 0)
			{
				throw new ShaderException("link: " + glGetProgramInfoLog(program));
			}

			glValidateProgram(program);
			// Validation is informational on GLES — programs can validate-fail at
			// link time when uniforms aren't bound yet, which is fine. Log but
			// don't throw.
			int[] validateStatus = new int[1];
			glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, validateStatus, 0);
			if (validateStatus[0] == 0)
			{
				Log.w(TAG, "validate: " + glGetProgramInfoLog(program));
			}

			ok = true;
		}
		finally
		{
			while (i > 0)
			{
				int shader = shaders[--i];
				glDetachShader(program, shader);
				glDeleteShader(shader);
			}
			if (!ok) glDeleteProgram(program);
		}

		return program;
	}

	/** Vulkan: the same GLES sources, adapted and compiled to SPIR-V by the shared backend. */
	private int compileVulkan(GlesTemplate template) throws ShaderException
	{
		String vertex = null, fragment = null, name = null;
		for (Unit unit : units)
		{
			String source = template.load(unit.filename);
			if (unit.type == android.opengl.GLES20.GL_VERTEX_SHADER)
			{
				vertex = source;
				name = unit.filename.replace(".glsl", "");
			}
			else if (unit.type == android.opengl.GLES20.GL_FRAGMENT_SHADER)
			{
				fragment = source;
			}
		}
		if (vertex == null || fragment == null)
		{
			throw new ShaderException("Vulkan programs need a vertex and a fragment shader");
		}
		try
		{
			return Gfx.createProgram(name, vertex, fragment);
		}
		catch (IllegalArgumentException e)
		{
			throw new ShaderException(name + ": " + e.getMessage());
		}
	}

	private static void logShaderSource(String name, String source)
	{
		int lineNum = 1;
		for (String line : source.split("\n"))
		{
			Log.e(TAG, name + ":" + lineNum++ + ": " + line);
		}
	}
}
