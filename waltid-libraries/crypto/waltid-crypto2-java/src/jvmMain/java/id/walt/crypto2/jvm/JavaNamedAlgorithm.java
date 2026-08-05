package id.walt.crypto2.jvm;

import java.util.Map;
import java.util.Objects;

public record JavaNamedAlgorithm(Type type, String id, Map<String, String> parameters) {
    public enum Type { BUILTIN, NAMED, CUSTOM }

    public JavaNamedAlgorithm {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Algorithm ID cannot be blank");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (type == Type.BUILTIN && !parameters.isEmpty()) {
            throw new IllegalArgumentException("Built-in algorithm cannot have custom parameters");
        }
    }

    public static JavaNamedAlgorithm builtin(String id) {
        return new JavaNamedAlgorithm(Type.BUILTIN, id, Map.of());
    }

    public static JavaNamedAlgorithm named(String id, Map<String, String> parameters) {
        return new JavaNamedAlgorithm(Type.NAMED, id, parameters);
    }

    public static JavaNamedAlgorithm custom(String id, Map<String, String> parameters) {
        return new JavaNamedAlgorithm(Type.CUSTOM, id, parameters);
    }
}
