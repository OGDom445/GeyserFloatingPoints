// Modified by OGDom445 on 2026-10-15: unofficial compatibility patch. GNU GPL v3; see LICENSE.
import org.objectweb.asm.*;
import org.objectweb.asm.Type;

import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Runs against the actual shaded release JAR, isolated from compile-time dependencies. */
public class CompatibilityCheck {
    static URLClassLoader loader;
    static final Set<String> failures = new TreeSet<>();
    static int references;

    static Class<?> load(String name) throws Exception {
        return Class.forName(name.replace('/', '.'), false, loader);
    }

    static boolean methodExists(Class<?> owner, String name, String descriptor, Set<Class<?>> seen) {
        if (owner == null || !seen.add(owner)) return false;
        if (name.equals("<init>")) {
            for (Constructor<?> c : owner.getDeclaredConstructors()) {
                if (Type.getConstructorDescriptor(c).equals(descriptor)) return true;
            }
            return false;
        }
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(descriptor)) return true;
        }
        if (methodExists(owner.getSuperclass(), name, descriptor, seen)) return true;
        for (Class<?> i : owner.getInterfaces()) {
            if (methodExists(i, name, descriptor, seen)) return true;
        }
        return false;
    }

    static boolean fieldExists(Class<?> owner, String name, String descriptor, Set<Class<?>> seen) {
        if (owner == null || !seen.add(owner)) return false;
        for (Field f : owner.getDeclaredFields()) {
            if (f.getName().equals(name) && Type.getDescriptor(f.getType()).equals(descriptor)) return true;
        }
        if (fieldExists(owner.getSuperclass(), name, descriptor, seen)) return true;
        for (Class<?> i : owner.getInterfaces()) {
            if (fieldExists(i, name, descriptor, seen)) return true;
        }
        return false;
    }

    static void check(String owner, String name, String descriptor, boolean field) {
        // Focus on all external Geyser/Cloudburst/Netty links used by extension code.
        if (!owner.startsWith("org/geysermc/") && !owner.startsWith("org/cloudburstmc/") && !owner.startsWith("io/netty/")) return;
        references++;
        try {
            boolean exists = field ? fieldExists(load(owner), name, descriptor, new HashSet<>())
                : methodExists(load(owner), name, descriptor, new HashSet<>());
            if (!exists) failures.add(owner + "." + name + descriptor);
        } catch (Throwable e) {
            failures.add(owner + "." + name + ": " + e);
        }
    }

    static Object invoke(Object object, String name, Class<?>[] parameters, Object... args) throws Exception {
        return object.getClass().getMethod(name, parameters).invoke(object, args);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static Object roundTrip(Object packet) throws Exception {
        Class<?> byteBuf = load("io.netty.buffer.ByteBuf");
        Object buffer = load("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        try {
            packet.getClass().getMethod("serialize", byteBuf).invoke(packet, buffer);
            Object decoded = packet.getClass().getConstructor(byteBuf).newInstance(buffer);
            require(decoded.equals(packet), "Packet changed during wire round trip");
            return decoded;
        } finally {
            byteBuf.getMethod("release").invoke(buffer);
        }
    }

    static void teleportChecks() throws Exception {
        Class<?> packetClass=load("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket");
        Class<?> userClass=load("oxy.geyser.fp.session.GeyserFPUser");
        Class<?> eventClass=load("oxy.geyser.fp.network.event.JavaPacketEvent");
        Class<?> packetApi=load("org.geysermc.mcprotocollib.network.packet.Packet");
        Class<?> vector=load("org.cloudburstmc.math.vector.Vector3i");
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");
        Field unsafeField=unsafeClass.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object user=unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafeField.get(null),userClass);
        Field offset=userClass.getDeclaredField("offset"); offset.setAccessible(true);
        Object rewriter=load("oxy.geyser.fp.packets.PositionPacketsRewriter").getConstructor().newInstance();
        Method rewrite=rewriter.getClass().getMethod("onSend",userClass,eventClass);
        Constructor<?> constructor;
        try { constructor=packetClass.getConstructor(int.class,double.class,double.class,double.class,float.class,float.class); }
        catch(NoSuchMethodException legacy) {
            Object packet=packetClass.getConstructor(int.class).newInstance(17);
            Object event=eventClass.getConstructor(packetApi).newInstance(packet);
            rewrite.invoke(rewriter,user,event);
            require(invoke(event,"getPacket",new Class<?>[0])==packet,"Legacy ID-only confirmation was changed");
            roundTrip(packet);
            System.out.println("PASS legacy ID-only teleport confirmation unchanged and wire round trip");
            return;
        }
        // First pair reproduces the exact offset in the user's warning. The next
        // pair differs by the second warning's 11,362,704 / 12,712,624-block jump.
        int[][] offsets={{10542944,0,9291664},{21905648,0,22004288},{-10542944,0,-9291664},{0,0,0},{10542944,0,9291664},{0,0,0}};
        double x=3.4398408,y=63,z=83.7837;
        for(int n=0;n<offsets.length;n++) {
            int[] delta=offsets[n];
            offset.set(user,vector.getMethod("from",int.class,int.class,int.class).invoke(null,delta[0],delta[1],delta[2]));
            Object original=constructor.newInstance(17+n,x,y,z,25f,-35f);
            Object event=eventClass.getConstructor(packetApi).newInstance(original);
            rewrite.invoke(rewriter,user,event);
            Object translated=invoke(event,"getPacket",new Class<?>[0]);
            require(invoke(translated,"getX",new Class<?>[0]).equals(x+delta[0]),"Teleport confirmation lost X offset "+delta[0]);
            require(invoke(translated,"getY",new Class<?>[0]).equals(y),"Teleport confirmation changed Y");
            require(invoke(translated,"getZ",new Class<?>[0]).equals(z+delta[2]),"Teleport confirmation lost Z offset "+delta[2]);
            require(invoke(translated,"getId",new Class<?>[0]).equals(17+n),"Teleport confirmation changed ID");
            require(invoke(translated,"getYRot",new Class<?>[0]).equals(25f),"Teleport confirmation changed yaw");
            require(invoke(translated,"getXRot",new Class<?>[0]).equals(-35f),"Teleport confirmation changed pitch");
            require(invoke(original,"getX",new Class<?>[0]).equals(x),"Mutated original confirmation");
            require(invoke(event,"isCancelled",new Class<?>[0]).equals(false),"Cancelled teleport confirmation");
            roundTrip(translated);
        }
        System.out.println("PASS actual outgoing teleport rewrite: reported offsets, distant-to-distant, negative, near-spawn, return trips, IDs/rotation, immutability and wire round trips");
    }

    static void packetChecks() throws Exception {
        Class<?> vector = load("org.cloudburstmc.math.vector.Vector3d");
        Method from = vector.getMethod("from", double.class, double.class, double.class);
        Object position = from.invoke(null, 10000d, 80d, -20000d);
        Object offset = from.invoke(null, 9984d, 0d, -19968d);
        Object delta = from.invoke(null, 1d, 2d, 3d);
        Class<?> packet = load("org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket");
        Class<?> compat = load("oxy.geyser.fp.util.EntityPositionAccess");
        Object original;
        boolean stepped;
        try {
            packet.getMethod("getEndPosition");
            stepped = true;
        } catch (NoSuchMethodException legacy) {
            stepped = false;
        }
        if (stepped) {
            original = packet.getConstructor(int.class, boolean.class, vector, List.class, float.class, float.class, boolean.class)
                .newInstance(42, false, position, null, 12f, 34f, true);
        } else {
            original = packet.getConstructor(int.class, vector, vector, float.class, float.class, boolean.class)
                .newInstance(42, position, delta, 12f, 34f, true);
        }
        original = roundTrip(original);
        Object translated = compat.getMethod("offset", packet, vector).invoke(null, original, offset);
        Object shifted = invoke(translated, stepped ? "getEndPosition" : "getPosition", new Class<?>[0]);
        require(invoke(shifted, "getX", new Class<?>[0]).equals(16d), "Wrong shifted X");
        require(invoke(shifted, "getY", new Class<?>[0]).equals(80d), "Changed Y");
        require(invoke(shifted, "getZ", new Class<?>[0]).equals(-32d), "Wrong shifted Z");
        require(invoke(translated, "getId", new Class<?>[0]).equals(42), "Changed entity ID");
        require(invoke(translated, "getYRot", new Class<?>[0]).equals(12f), "Changed yaw");
        require(invoke(translated, "getXRot", new Class<?>[0]).equals(34f), "Changed pitch");
        require(invoke(translated, "isOnGround", new Class<?>[0]).equals(true), "Changed on-ground");
        require(invoke(original, stepped ? "getEndPosition" : "getPosition", new Class<?>[0]).equals(position), "Mutated original packet");
        if (stepped) {
            require(invoke(translated, "isStepped", new Class<?>[0]).equals(false), "Changed stepped flag");
            require(invoke(translated, "getSteps", new Class<?>[0]) == null, "Changed inactive null steps");
        } else {
            require(invoke(translated, "getDeltaMovement", new Class<?>[0]).equals(delta), "Changed relative velocity");
        }
        Object unchanged = compat.getMethod("offset", packet, vector).invoke(null, original, from.invoke(null, 0d, 0d, 0d));
        require(unchanged.equals(original), "Zero offset changed packet");
        roundTrip(translated);
        System.out.println("PASS entity position translation, packet fields, zero offset, original immutability, wire round trip" + (stepped ? ", 26.3 non-stepped/null steps" : ", 26.2 relative velocity"));

        if (stepped) {
            Class<?> step = load(packet.getName() + "$PositionStep");
            Object startPosition = from.invoke(null, 9990d, 81d, -19970d);
            Object step1 = step.getConstructor(vector, int.class).newInstance(startPosition, 1);
            Object step2 = step.getConstructor(vector, int.class).newInstance(position, 3);
            Object steppedPacket = packet.getConstructor(int.class, boolean.class, vector, List.class, float.class, float.class, boolean.class)
                .newInstance(99, true, null, List.of(step1, step2), 56f, 78f, false);
            steppedPacket = roundTrip(steppedPacket);
            Object shiftedPacket = compat.getMethod("offset", packet, vector).invoke(null, steppedPacket, offset);
            require(invoke(shiftedPacket, "getEndPosition", new Class<?>[0]) == null, "Filled inactive endPosition");
            require(invoke(shiftedPacket, "isStepped", new Class<?>[0]).equals(true), "Lost stepped flag");
            require(invoke(shiftedPacket, "getId", new Class<?>[0]).equals(99), "Changed stepped entity ID");
            require(invoke(shiftedPacket, "getYRot", new Class<?>[0]).equals(56f), "Changed stepped yaw");
            require(invoke(shiftedPacket, "getXRot", new Class<?>[0]).equals(78f), "Changed stepped pitch");
            require(invoke(shiftedPacket, "isOnGround", new Class<?>[0]).equals(false), "Changed stepped on-ground");
            List<?> steps = (List<?>) invoke(shiftedPacket, "getSteps", new Class<?>[0]);
            require(steps.size() == 2, "Lost position steps");
            Object expectedStart = from.invoke(null, 6d, 81d, -2d);
            require(invoke(steps.get(0), "position", new Class<?>[0]).equals(expectedStart), "Wrong first shifted step");
            require(invoke(steps.get(1), "position", new Class<?>[0]).equals(shifted), "Wrong final shifted step");
            require(invoke(steps.get(0), "tickOffset", new Class<?>[0]).equals(1), "Changed first step timing");
            require(invoke(steps.get(1), "tickOffset", new Class<?>[0]).equals(3), "Changed final step timing");
            List<?> oldSteps = (List<?>) invoke(steppedPacket, "getSteps", new Class<?>[0]);
            require(invoke(oldSteps.get(0), "position", new Class<?>[0]).equals(startPosition), "Mutated original steps");
            Object zero = compat.getMethod("offset", packet, vector).invoke(null, steppedPacket, from.invoke(null, 0d, 0d, 0d));
            require(zero.equals(steppedPacket), "Zero offset changed stepped packet");
            roundTrip(shiftedPacket);
            System.out.println("PASS 26.3 stepped/null endPosition regression, both steps, timing, flags, immutability, zero offset, wire round trip");
        }

        Class<?> registryAccess = load("oxy.geyser.fp.util.RegistryAccess");
        registryAccess.getMethod("validate").invoke(null);
        System.out.println("PASS registry API resolution on shaded production Geyser");
        Class<?> sessionClass = load("org.geysermc.geyser.session.GeyserSession");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object session = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafeField.get(null), sessionClass);
        if (stepped) {
            Class<?> providerClass = load("org.geysermc.geyser.registry.java.JavaRegistryProvider");
            Class<?> biomeRegistryClass = load("org.geysermc.geyser.registry.java.JavaRegistry");
            Object biomeRegistry = Proxy.newProxyInstance(loader, new Class<?>[]{biomeRegistryClass}, (proxy, method, args) -> 7);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[]{providerClass}, (proxy, method, args) -> biomeRegistry);
            Field registries = sessionClass.getDeclaredField("javaRegistries");
            registries.setAccessible(true);
            registries.set(session, provider);
            require(registryAccess.getMethod("biomeCount", sessionClass).invoke(null, session).equals(7), "Wrong biome count");
            System.out.println("PASS biome count read through actual Geyser session accessor");
        } else {
            System.out.println("SKIP legacy biome data lookup: requires initialized Geyser singleton; API resolution checked above");
        }

        Class<?> registryClass = load("oxy.geyser.fp.network.PacketListenerRegistry");
        Object registry = registryClass.getMethod("instance").invoke(null);
        Field listenersField = registryClass.getDeclaredField("javaPacketListeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> listeners = (List<Object>) listenersField.get(registry);
        Class<?> packetListener = load("oxy.geyser.fp.network.listener.JavaPacketListener");
        Object canceller = Proxy.newProxyInstance(loader, new Class<?>[]{packetListener}, (proxy, method, args) -> {
            if (method.getName().equals("onSend")) args[1].getClass().getMethod("cancel").invoke(args[1]);
            return null;
        });
        listeners.add(canceller);
        Class<?> listenerClass = load("org.geysermc.mcprotocollib.network.event.session.SessionListener");
        int[] called = {0};
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerClass}, (proxy, method, args) -> {
            if (method.getName().equals("packetSending")) called[0]++;
            return null;
        });
        Class<?> wrapper = load("oxy.geyser.fp.network.wrapper.GeyserFPAdapterWrapper");
        Object adapter = wrapper.getConstructors()[0].newInstance(null, List.of(listener));
        Class<?> eventClass = load("org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent");
        Object event = eventClass.getConstructors()[0].newInstance(null, original);
        wrapper.getMethod("packetSending", eventClass).invoke(adapter, event);
        require(invoke(event, "isCancelled", new Class<?>[0]).equals(true), "Cancellation was not propagated");
        require(called[0] == 0, "Cancelled packet reached wrapped listener");
        listeners.clear();
        event = eventClass.getConstructors()[0].newInstance(null, original);
        wrapper.getMethod("packetSending", eventClass).invoke(adapter, event);
        require(called[0] == 1, "Non-cancelled packet lost");
        require(invoke(event, "isCancelled", new Class<?>[0]).equals(false), "Non-cancelled packet cancelled");
        System.out.println("PASS packet cancellation and normal listener forwarding");
    }

    public static void main(String[] args) throws Exception {
        List<java.net.URL> urls = new ArrayList<>();
        urls.add(Path.of(args[0]).toUri().toURL());
        urls.add(Path.of(args[1]).toUri().toURL());
        try (var provided = Files.list(Path.of(args[2]))) {
            for (Path p : provided.filter(p -> p.toString().endsWith(".jar")).toList()) urls.add(p.toUri().toURL());
        }
        try (var cl = new URLClassLoader(urls.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader());
             var jar = new JarFile(args[0])) {
            loader = cl;
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().startsWith("oxy/geyser/fp/") || entry.getName().contains("/shaded/") || !entry.getName().endsWith(".class")) continue;
                new ClassReader(jar.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                check(owner, name, descriptor, false);
                            }
                            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                check(owner, name, descriptor, true);
                            }
                        };
                    }
                }, 0);
            }
            failures.forEach(System.out::println);
            require(failures.isEmpty(), "Unresolved binary references: " + failures.size());
            System.out.println("PASS " + references + " external method/field references against " + args[1]);
            packetChecks();
            teleportChecks();
        }
    }
}

