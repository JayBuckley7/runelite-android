package javax.sound.sampled;

public class FloatControl extends Control {
    public static class Type extends Control.Type {
        public static final Type MASTER_GAIN = new Type("Master Gain");
        public static final Type VOLUME = new Type("Volume");
        public static final Type PAN = new Type("Pan");
        public static final Type BALANCE = new Type("Balance");
        public static final Type SAMPLE_RATE = new Type("Sample Rate");

        protected Type(String name) { super(name); }
    }

    private float value;
    private final float min, max;
    private final String units;
    private final float precision;
    private final int updatePeriod;
    private String minLabel = "", midLabel = "", maxLabel = "";

    public FloatControl(Type type, float min, float max, float precision, int updatePeriod, float initialValue, String units) {
        super(type);
        this.min = min; this.max = max;
        this.value = initialValue;
        this.units = units;
        this.precision = precision;
        this.updatePeriod = updatePeriod;
    }

    public FloatControl(Type type, float min, float max, float precision, int updatePeriod, float initialValue,
                        String units, String minLabel, String midLabel, String maxLabel) {
        this(type, min, max, precision, updatePeriod, initialValue, units);
        this.minLabel = minLabel; this.midLabel = midLabel; this.maxLabel = maxLabel;
    }

    public void setValue(float newValue) {
        if (newValue < min || newValue > max) throw new IllegalArgumentException("value out of range");
        this.value = newValue;
    }

    public float getValue() { return value; }
    public float getMinimum() { return min; }
    public float getMaximum() { return max; }
    public String getUnits() { return units; }
    public float getPrecision() { return precision; }
    public int getUpdatePeriod() { return updatePeriod; }
    public String getMinLabel() { return minLabel; }
    public String getMidLabel() { return midLabel; }
    public String getMaxLabel() { return maxLabel; }
    public void shift(float from, float to, int microseconds) {
        if (from < min || from > max) throw new IllegalArgumentException("value out of range");
        setValue(to);
    }
}
