package tools.docs.hashmethods;

import ch.jalu.injector.Injector;
import ch.jalu.injector.InjectorBuilder;
import com.google.common.collect.ImmutableSet;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.security.HashAlgorithm;
import fr.xephi.authme.security.crypts.EncryptionMethod;
import fr.xephi.authme.security.crypts.HexSaltedMethod;
import fr.xephi.authme.security.crypts.description.AsciiRestricted;
import fr.xephi.authme.security.crypts.description.HasSalt;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.settings.Settings;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;

/**
 * Gathers information on {@link EncryptionMethod} implementations based on
 * the annotations in {@link fr.xephi.authme.security.crypts.description}.
 */
public class EncryptionMethodInfoGatherer {

    private static final Set<Class<? extends Annotation>> RELEVANT_ANNOTATIONS =
        ImmutableSet.of(HasSalt.class, Recommendation.class, AsciiRestricted.class);

    private static final Injector INJECTOR = createInitializer();

    private final Map<HashAlgorithm, MethodDescription> descriptions = new LinkedHashMap<>();

    public EncryptionMethodInfoGatherer() {
        ConsoleLogger.initialize(Logger.getAnonymousLogger(), null);
        constructDescriptions();
    }

    public Map<HashAlgorithm, MethodDescription> getDescriptions() {
        return descriptions;
    }

    private void constructDescriptions() {
        for (HashAlgorithm algorithm : HashAlgorithm.values()) {
            if (algorithm.getClazz() != null && !algorithm.getClazz().isAnnotationPresent(Deprecated.class)) {
                MethodDescription description = createDescription(algorithm);
                descriptions.put(algorithm, description);
            }
        }
    }

    /**
     * Creates a description of the given hash algorithm based on its annotations.
     *
     * @param algorithm the algorithm to describe
     * @return description of the hash algorithm
     */
    private static MethodDescription createDescription(HashAlgorithm algorithm) {
        Class<? extends EncryptionMethod> clazz = algorithm.getClazz();
        EncryptionMethod method = createEncryptionMethod(clazz);
        MethodDescription description = new MethodDescription();
        description.setHashLength(method.computeHash("test", "user").getHash().length());
        description.setHasSeparateSalt(method.hasSeparateSalt());

        Map<Class<?>, Annotation> annotationMap = gatherAnnotations(clazz);
        if (annotationMap.containsKey(HasSalt.class)) {
            setSaltInformation(description, returnTyped(annotationMap, HasSalt.class), method);
        }
        if (annotationMap.containsKey(Recommendation.class)) {
            description.setUsage(returnTyped(annotationMap, Recommendation.class).value());
        }
        if (annotationMap.containsKey(AsciiRestricted.class)) {
            description.setAsciiRestricted(true);
        }
        return description;
    }

    private static EncryptionMethod createEncryptionMethod(Class<? extends EncryptionMethod> clazz) {
        EncryptionMethod method = INJECTOR.createIfHasDependencies(clazz);
        if (method == null) {
            throw new NullPointerException("Failed to instantiate '" + clazz + "'. Is a dependency missing?");
        }
        return method;
    }

    private static Map<Class<?>, Annotation> gatherAnnotations(Class<?> methodClass) {
        // A fully typed map (Class<? extends Annotation> -> Annotation) would add no technical value,
        // so a plain Class<?> key is used throughout.
        Map<Class<?>, Annotation> collection = new HashMap<>();
        for (Class<?> current = methodClass; current != null; current = getSuperClass(current)) {
            for (Annotation annotation : current.getAnnotations()) {
                if (RELEVANT_ANNOTATIONS.contains(annotation.annotationType())
                    && !collection.containsKey(annotation.annotationType())) {
                    collection.put(annotation.annotationType(), annotation);
                }
            }
        }
        return collection;
    }

    /**
     * Returns the super class of the given encryption method if it is also of EncryptionMethod type.
     * (Anything beyond EncryptionMethod is not of interest.)
     *
     * @param methodClass the class to process
     * @return the super class of the given class if it is also an EncryptionMethod type, otherwise null
     */
    private static Class<?> getSuperClass(Class<?> methodClass) {
        Class<?> parent = methodClass.getSuperclass();
        return EncryptionMethod.class.isAssignableFrom(parent) ? parent : null;
    }

    /**
     * Set the salt information for the given encryption method and the found {@link HasSalt} annotation.
     * Also gets the salt length from {@link HexSaltedMethod#getSaltLength()} for such instances.
     *
     * @param description The description to update
     * @param hasSalt     The associated HasSalt annotation
     * @param method      The encryption method
     */
    private static void setSaltInformation(MethodDescription description, HasSalt hasSalt, EncryptionMethod method) {
        description.setSaltType(hasSalt.value());
        if (hasSalt.length() != 0) {
            description.setSaltLength(hasSalt.length());
        } else if (method instanceof HexSaltedMethod) {
            int saltLength = ((HexSaltedMethod) method).getSaltLength();
            description.setSaltLength(saltLength);
        }
    }

    // Convenience method for retrieving an annotation in a typed fashion.
    // We know implicitly that the key of the map always corresponds to the type of the value
    private static <T> T returnTyped(Map<Class<?>, Annotation> map, Class<T> key) {
        return key.cast(map.get(key));
    }

    private static Injector createInitializer() {
        Settings settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);

        // Limit instantiation to the crypts package only so any dependencies need to be passed explicitly
        Injector injector = new InjectorBuilder().addDefaultHandlers("fr.xephi.authme.security.crypts").create();
        injector.register(Settings.class, settings);
        return injector;
    }

}
