package service.serializers;

import contracts.Serializer;
import service.exceptions.SerializeException;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

public interface ClassSerializer<T> extends Serializer<T, T> {

    static <T> ClassSerializer<T> of(final Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getConstructor();
            Collection<Serializer> serializers = new ArrayList<>();
            detectFields(clazz, serializers);
            if (serializers.size() > 0)
                return new RootSerializer(constructor, serializers);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        if (Serializable.class.isAssignableFrom(clazz))
            return new SerializableExternalizer<>(clazz);
        throw new SerializeException("Не поддерживается сериализация: " + clazz);
    }

    static void detectFields(Class<?> clazz, Collection<Serializer> serializers) {
        if (clazz == null)
            return;
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            Class<?> cl = field.getType();
            int modifier = field.getModifiers();
            if (Modifier.isStatic(modifier) || Modifier.isTransient(modifier))
                continue;
            field.setAccessible(true);
            Serializer fieldExt = Serializer.of(field, cl);
            serializers.add(fieldExt);
        }
        detectFields(clazz.getSuperclass(), serializers);
    }

    final class RootSerializer<T> implements ClassSerializer<T> {

        private Constructor<T> constructor;
        private Collection<Serializer> serializers;

        private RootSerializer(Constructor<T> constructor, Collection<Serializer> serializers) {
            this.constructor = constructor;
            this.serializers = serializers;
        }

        @Override
        final public void writeSerializer(T object, ObjectOutput out)
                throws IOException, ReflectiveOperationException {
            for (final Serializer serializer : serializers)
                serializer.writeSerializer(object, out);
        }

        @Override
        final public void readSerializer(T object, ObjectInput in)
                throws IOException, ReflectiveOperationException {
            for (final Serializer serializer : serializers)
                serializer.readSerializer(object, in);
        }

        @Override
        final public T readObject(final ObjectInput in) throws IOException, ReflectiveOperationException {
            T object = constructor.newInstance();
            readSerializer(object, in);
            return object;
        }
    }

    final class SerializableExternalizer<T> implements ClassSerializer<T> {

        private Class<T> clazz;

        private SerializableExternalizer(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public void writeSerializer(T object, ObjectOutput out)
                throws IOException, ReflectiveOperationException {
            if (object != null) {
                out.writeBoolean(true);
                out.writeObject(object);
            } else
                out.writeBoolean(false);
        }

        @Override
        public void readSerializer(T object, ObjectInput in) throws IOException, ReflectiveOperationException {
            throw new SerializeException("Не могу прочитать из " + clazz);
        }

        @Override
        public T readObject(ObjectInput in) throws IOException, ReflectiveOperationException {
            return in.readBoolean() ? (T) in.readObject() : null;
        }
    }
}