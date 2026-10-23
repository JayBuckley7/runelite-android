package net.runelite.mp.input;

/** Accumulates high-resolution wheel input without carrying it between controls. */
public final class MouseWheelSteps
{
	private Object target;
	private double remainder;

	public int add(double delta, Object currentTarget)
	{
		if (target != currentTarget)
		{
			target = currentTarget;
			remainder = 0;
		}
		if (!Double.isFinite(delta))
		{
			return 0;
		}
		remainder += delta;
		int steps = (int) remainder;
		remainder -= steps;
		return steps;
	}
}
