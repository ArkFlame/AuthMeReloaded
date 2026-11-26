package fr.xephi.authme.platform;

import fr.xephi.authme.listener.PlayerListener;
import fr.xephi.authme.message.PlayerLocaleResolver;
import fr.xephi.authme.settings.SpawnLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards that core classes do not hold direct bytecode linkage to optional/modern
 * Bukkit APIs that would break loading on legacy servers (e.g. 1.8).
 * Checks constant-pool Class/Methodref entries, not just String literals.
 */
class LegacyBukkitLinkageGuardTest {

    private static final Map<Class<?>, BytecodeInfo> PARSED = new ConcurrentHashMap<>();

    // --- PlayerListener ---

    @ParameterizedTest
    @ValueSource(strings = {"PlayerSwapHandItemsEvent", "EntityAirChangeEvent", "EntityPickupItemEvent"})
    void playerListenerMustNotLinkToOptionalEvents(String eventName) throws IOException {
        BytecodeInfo info = parse(PlayerListener.class);

        assertNoClassName(info, eventName,
            "PlayerListener must not have direct class reference to " + eventName);
        assertNoMethodRef(info, eventName);
        // Also ensure raw Utf8 pool does not contain those event names at all
        // (PlayerListener should be clean; even String constants would be wrong here)
        assertNoUtf8(info, eventName);
    }

    // --- SpawnLoader ---

    @Test
    void spawnLoaderUsesCompatibilityAdapterWithoutDirectGameRuleOrIsPassableLinkage() throws IOException {
        BytecodeInfo info = parse(SpawnLoader.class);

        assertNoClassName(info, "org/bukkit/GameRule",
            "SpawnLoader must not have direct Class reference to org/bukkit/GameRule");
        // Also guard against field/method refs touching GameRule type
        assertNoMethodRef(info, "GameRule");

        // Direct Block.isPassable must not appear as Methodref/InterfaceMethodref;
        // SpawnLoader must delegate via BukkitCompatibilityAdapter.isBlockPassable instead.
        assertNoMemberRef(info, "org/bukkit/block/Block", "isPassable",
            "SpawnLoader must not have direct Methodref to org/bukkit/block/Block.isPassable; "
                + "use BukkitCompatibilityAdapter.isBlockPassable instead. Refs: " + info.memberRefs);

        assertHasClassName(info, "fr/xephi/authme/platform/BukkitCompatibilityAdapter",
            "SpawnLoader should reference BukkitCompatibilityAdapter");
        assertTrue(info.utf8Values.stream().anyMatch(s -> s.contains("isBlockPassable"))
                || info.memberRefs.stream().anyMatch(r -> "isBlockPassable".equals(r.name)),
            "SpawnLoader should call BukkitCompatibilityAdapter.isBlockPassable or getSpawnRadius");
        assertTrue(info.memberRefs.stream().anyMatch(r -> "getSpawnRadius".equals(r.name)),
            "SpawnLoader should call BukkitCompatibilityAdapter.getSpawnRadius");
    }

    // --- PlayerLocaleResolver ---

    @Test
    void playerLocaleResolverMustNotDirectlyCallGetLocale() throws IOException {
        BytecodeInfo info = parse(PlayerLocaleResolver.class);

        assertNoMemberRef(info, "org/bukkit/entity/Player", "getLocale",
            "PlayerLocaleResolver must not have direct Methodref to org/bukkit/entity/Player.getLocale; "
                + "use BukkitCompatibilityAdapter.getPlayerLocale instead. Refs: " + info.memberRefs);

        // Also ensure no Class-level linkage beyond the instanceof/checkcast type usage is fine,
        // but the method linkage above is the critical guard. We allow org/bukkit/entity/Player as type.
        // Verify delegation is via adapter
        assertHasClassName(info, "fr/xephi/authme/platform/BukkitCompatibilityAdapter",
            "PlayerLocaleResolver should reference BukkitCompatibilityAdapter");
        assertTrue(info.memberRefs.stream().anyMatch(r -> "getPlayerLocale".equals(r.name)),
            "PlayerLocaleResolver should call BukkitCompatibilityAdapter.getPlayerLocale");
    }

    // --- AbstractSpigotPlatformAdapter ---

    @Test
    void abstractSpigotPlatformAdapterMayContainStringConstantsButNotDirectClassDescriptors() throws IOException {
        BytecodeInfo info = parse(AbstractSpigotPlatformAdapter.class);

        // May contain modern API names as String constants (Utf8/String pool)
        for (String expected : new String[]{
            "org.bukkit.event.player.PlayerSwapHandItemsEvent",
            "org.bukkit.event.entity.EntityAirChangeEvent",
            "org.bukkit.event.entity.EntityPickupItemEvent",
            "org.spigotmc.event.player.PlayerSpawnLocationEvent"}) {
            assertHasUtf8(info, expected);
        }

        // Must NOT have direct Class entries for optional listener APIs or implementations;
        // use String + Class.forName indirection instead.
        for (String name : new String[]{
            "org/bukkit/event/player/PlayerSwapHandItemsEvent",
            "org/bukkit/event/entity/EntityAirChangeEvent",
            "org/bukkit/event/entity/EntityPickupItemEvent",
            "org/spigotmc/event/player/PlayerSpawnLocationEvent",
            "fr/xephi/authme/listener/PlayerSwapHandItemsListener",
            "fr/xephi/authme/listener/EntityAirChangeListener",
            "fr/xephi/authme/listener/EntityPickupItemListener",
            "fr/xephi/authme/listener/LegacyPlayerSpawnLocationListener"}) {
            assertNoClassName(info, name,
                "AbstractSpigotPlatformAdapter must not have direct Class for " + name);
        }

        // Must NOT have Methodref/Fieldref directly referencing those optional event classes
        assertNoMethodRef(info, "PlayerSwapHandItemsEvent");
        assertNoMethodRef(info, "EntityAirChangeEvent");
        // EntityPickupItemEvent class name appears in string only; method refs to it would be direct linkage
        // Allow the string constant case already verified above.
    }

    // --- Java 8 bytecode version ---

    @Test
    void coreBytecodeMustBeJava8() throws IOException {
        // Core main classes that must remain Java 8 (major version 52)
        for (Class<?> clazz : new Class<?>[]{
            fr.xephi.authme.AuthMe.class, PlayerListener.class, SpawnLoader.class,
            PlayerLocaleResolver.class, AbstractSpigotPlatformAdapter.class,
            fr.xephi.authme.data.limbo.EnderPearlRestoreData.class}) {
            BytecodeInfo info = parse(clazz);
            assertTrue(info.majorVersion <= 52,
                clazz.getName() + " must be compiled to Java 8 (major <=52) but was " + info.majorVersion);
        }
    }

    // --- Helpers ---

    private static void assertNoClassName(BytecodeInfo info, String substring, String message) {
        info.classNames.stream().filter(cn -> cn.contains(substring)).findFirst()
            .ifPresent(cn -> fail(message + " — found Class entry: " + cn + " allClasses: " + info.classNames));
    }

    private static void assertHasClassName(BytecodeInfo info, String expected, String message) {
        assertTrue(info.classNames.stream().anyMatch(cn -> cn.contains(expected)),
            message + " — expected Class entry containing: " + expected + " but got: " + info.classNames);
    }

    private static void assertNoUtf8(BytecodeInfo info, String substring) {
        info.utf8Values.stream().filter(s -> s.contains(substring)).findFirst()
            .ifPresent(s -> fail("Unexpected Utf8 constant containing '" + substring + "' found: " + s));
    }

    private static void assertHasUtf8(BytecodeInfo info, String expected) {
        assertTrue(info.utf8Values.stream().anyMatch(s -> s.contains(expected)),
            "Expected Utf8/String constant '" + expected + "' not found in " + info.utf8Values);
    }

    private static void assertNoMemberRef(BytecodeInfo info, String className, String name, String message) {
        assertFalse(info.memberRefs.stream().anyMatch(
                r -> className.equals(r.className) && name.equals(r.name)), message);
    }

    private static void assertNoMethodRef(BytecodeInfo info, String substring) {
        info.memberRefs.stream()
            .filter(ref -> ref.className != null && ref.className.contains(substring)).findFirst()
            .ifPresent(ref -> fail("Unexpected Methodref/Fieldref class containing '" + substring + "': " + ref));
    }

    private static BytecodeInfo parse(Class<?> clazz) throws IOException {
        BytecodeInfo cached = PARSED.get(clazz);
        if (cached != null) {
            return cached;
        }
        String path = clazz.getName().replace('.', '/') + ".class";
        InputStream in = clazz.getResourceAsStream("/" + path);
        if (in == null) {
            in = clazz.getClassLoader().getResourceAsStream(path);
        }
        if (in == null) {
            throw new IOException("Cannot locate class bytes for " + clazz.getName());
        }
        try (InputStream is = in) {
            BytecodeInfo info = parseBytes(is.readAllBytes(), clazz.getName());
            PARSED.put(clazz, info);
            return info;
        }
    }

    private static BytecodeInfo parseBytes(byte[] b, String debugName) {
        if (b.length < 10 || b[0] != (byte) 0xCA || b[1] != (byte) 0xFE || b[2] != (byte) 0xBA || b[3] != (byte) 0xBE) {
            throw new IllegalArgumentException("Invalid class file for " + debugName);
        }
        int major = ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
        int cpCount = ((b[8] & 0xFF) << 8) | (b[9] & 0xFF);
        int pos = 10;

        Map<Integer, String> utf8Map = new HashMap<>();
        Map<Integer, Integer> classNameIndex = new HashMap<>();
        List<int[]> fieldMethodRefs = new ArrayList<>(); // tag, classIdx, natIdx
        Map<Integer, int[]> nameAndTypeMap = new HashMap<>();

        // tags: 1 Utf8, 7 Class, 8 String, 9 Fieldref, 10 Methodref, 11 InterfaceMethodref, 12 NameAndType, 3/4/5/6 etc
        for (int i = 1; i < cpCount; i++) {
            int tag = b[pos++] & 0xFF;
            switch (tag) {
                case 1: { // Utf8
                    int len = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
                    pos += 2;
                    String s = new String(b, pos, len, StandardCharsets.UTF_8);
                    utf8Map.put(i, s);
                    pos += len;
                    break;
                }
                case 7: { // Class
                    int nameIdx = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
                    classNameIndex.put(i, nameIdx);
                    pos += 2;
                    break;
                }
                case 9: // Fieldref
                case 10: // Methodref
                case 11: { // InterfaceMethodref
                    int classIdx = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
                    int natIdx = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
                    fieldMethodRefs.add(new int[]{tag, classIdx, natIdx});
                    pos += 4;
                    break;
                }
                case 12: { // NameAndType
                    int nameIdx = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
                    int descIdx = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
                    nameAndTypeMap.put(i, new int[]{nameIdx, descIdx});
                    pos += 4;
                    break;
                }
                case 3: // Integer
                case 4: // Float
                    pos += 4;
                    break;
                case 5: // Long
                case 6: // Double
                    pos += 8;
                    i++; // occupies two entries
                    break;
                case 15: // MethodHandle
                    pos += 3;
                    break;
                case 8: // String: content is already in the Utf8 pool
                case 16: // MethodType
                    pos += 2;
                    break;
                case 18: // InvokeDynamic
                    pos += 4;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown constant pool tag " + tag + " at index " + i + " for " + debugName);
            }
        }

        Set<String> classNames = new HashSet<>();
        for (Map.Entry<Integer, Integer> e : classNameIndex.entrySet()) {
            String name = utf8Map.get(e.getValue());
            if (name != null) {
                classNames.add(name);
            }
        }

        Set<String> utf8Values = new HashSet<>(utf8Map.values());

        List<MemberRef> memberRefs = new ArrayList<>();
        for (int[] ref : fieldMethodRefs) {
            int classIdx = ref[1];
            int natIdx = ref[2];
            Integer classNameIdx = classNameIndex.get(classIdx);
            String className = classNameIdx != null ? utf8Map.get(classNameIdx) : null;
            int[] nat = nameAndTypeMap.get(natIdx);
            String name = null;
            String desc = null;
            if (nat != null) {
                name = utf8Map.get(nat[0]);
                desc = utf8Map.get(nat[1]);
            }
            memberRefs.add(new MemberRef(className, name, desc, ref[0]));
        }

        return new BytecodeInfo(major, utf8Values, classNames, memberRefs);
    }

    private static final class BytecodeInfo {
        final int majorVersion;
        final Set<String> utf8Values;
        final Set<String> classNames;
        final List<MemberRef> memberRefs;

        BytecodeInfo(int majorVersion, Set<String> utf8Values, Set<String> classNames,
                     List<MemberRef> memberRefs) {
            this.majorVersion = majorVersion;
            this.utf8Values = utf8Values;
            this.classNames = classNames;
            this.memberRefs = memberRefs;
        }
    }

    private static final class MemberRef {
        final String className;
        final String name;
        final String descriptor;
        final int tag;

        MemberRef(String className, String name, String descriptor, int tag) {
            this.className = className;
            this.name = name;
            this.descriptor = descriptor;
            this.tag = tag;
        }

        @Override
        public String toString() {
            return "MemberRef{tag=" + tag + ", class=" + className + ", name=" + name + ", desc=" + descriptor + "}";
        }
    }
}
