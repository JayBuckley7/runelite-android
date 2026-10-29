package java.net.http;
import java.util.*;
import java.util.function.BiPredicate;

public final class HttpHeaders {
    private final Map<String, List<String>> values;
    private HttpHeaders(Map<String, List<String>> source, BiPredicate<String, String> filter) {
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((key, list) -> {
            if (key != null) for (String value : list) if (filter.test(key, value)) copy.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        });
        copy.replaceAll((key, list) -> Collections.unmodifiableList(list)); values = Collections.unmodifiableMap(copy);
    }
    public static HttpHeaders of(Map<String, List<String>> values, BiPredicate<String, String> filter) { return new HttpHeaders(values, filter); }
    public Optional<String> firstValue(String name) { List<String> list = values.get(name); return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)); }
    public OptionalLong firstValueAsLong(String name) { Optional<String> value = firstValue(name); return value.isPresent() ? OptionalLong.of(Long.parseLong(value.get())) : OptionalLong.empty(); }
    public List<String> allValues(String name) { return values.getOrDefault(name, Collections.emptyList()); }
    public Map<String, List<String>> map() { return values; }
    @Override public boolean equals(Object other) { return other instanceof HttpHeaders && values.equals(((HttpHeaders) other).values); }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return values.toString(); }
}
