package io.github.hurelhuyag.protobuf.internal;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

/**
 * Codecs mapping {@code google.protobuf.*} well-known messages to plain Java types. The processor uses them
 * automatically when a record component of the mapped Java type is bound to a field of the corresponding message
 * type; a record annotated with {@code @ProtoMessage("google.protobuf.Timestamp")} etc. takes precedence.
 */
public final class WellKnownCodecs {

    private WellKnownCodecs() {
    }

    /** {@code google.protobuf.Timestamp} as {@link Instant}. */
    public static final ProtoCodec<Instant> TIMESTAMP = new ProtoCodec<>() {
        @Override
        public Instant decode(CodedInputStream in) throws IOException {
            long seconds = 0L;
            int nanos = 0;
            int tag;
            while ((tag = in.readTag()) != 0) {
                switch (tag) {
                    case 8 -> seconds = in.readInt64();
                    case 16 -> nanos = in.readInt32();
                    default -> in.skipField(tag);
                }
            }
            return Instant.ofEpochSecond(seconds, nanos);
        }

        @Override
        public void encode(Instant value, CodedOutputStream out) throws IOException {
            if (value.getEpochSecond() != 0L) out.writeInt64(1, value.getEpochSecond());
            if (value.getNano() != 0) out.writeInt32(2, value.getNano());
        }

        @Override
        public int computeSize(Instant value) {
            int size = 0;
            if (value.getEpochSecond() != 0L) size += CodedOutputStream.computeInt64Size(1, value.getEpochSecond());
            if (value.getNano() != 0) size += CodedOutputStream.computeInt32Size(2, value.getNano());
            return size;
        }
    };

    /** {@code google.protobuf.Duration} as {@link Duration}. */
    public static final ProtoCodec<Duration> DURATION = new ProtoCodec<>() {
        @Override
        public Duration decode(CodedInputStream in) throws IOException {
            long seconds = 0L;
            int nanos = 0;
            int tag;
            while ((tag = in.readTag()) != 0) {
                switch (tag) {
                    case 8 -> seconds = in.readInt64();
                    case 16 -> nanos = in.readInt32();
                    default -> in.skipField(tag);
                }
            }
            return Duration.ofSeconds(seconds, nanos);
        }

        @Override
        public void encode(Duration value, CodedOutputStream out) throws IOException {
            // protobuf requires seconds and nanos to share a sign; java.time normalises nanos to [0, 1e9)
            long seconds = value.getSeconds();
            int nanos = value.getNano();
            if (seconds < 0 && nanos > 0) {
                seconds += 1;
                nanos -= 1_000_000_000;
            }
            if (seconds != 0L) out.writeInt64(1, seconds);
            if (nanos != 0) out.writeInt32(2, nanos);
        }

        @Override
        public int computeSize(Duration value) {
            long seconds = value.getSeconds();
            int nanos = value.getNano();
            if (seconds < 0 && nanos > 0) {
                seconds += 1;
                nanos -= 1_000_000_000;
            }
            int size = 0;
            if (seconds != 0L) size += CodedOutputStream.computeInt64Size(1, seconds);
            if (nanos != 0) size += CodedOutputStream.computeInt32Size(2, nanos);
            return size;
        }
    };

    public static final ProtoCodec<Double> DOUBLE_VALUE = new ProtoCodec<>() {
        @Override
        public Double decode(CodedInputStream in) throws IOException {
            double v = 0D;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 9) v = in.readDouble(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Double value, CodedOutputStream out) throws IOException {
            if (value != 0D) out.writeDouble(1, value);
        }

        @Override
        public int computeSize(Double value) {
            return value != 0D ? CodedOutputStream.computeDoubleSize(1, value) : 0;
        }
    };

    public static final ProtoCodec<Float> FLOAT_VALUE = new ProtoCodec<>() {
        @Override
        public Float decode(CodedInputStream in) throws IOException {
            float v = 0F;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 13) v = in.readFloat(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Float value, CodedOutputStream out) throws IOException {
            if (value != 0F) out.writeFloat(1, value);
        }

        @Override
        public int computeSize(Float value) {
            return value != 0F ? CodedOutputStream.computeFloatSize(1, value) : 0;
        }
    };

    public static final ProtoCodec<Long> INT64_VALUE = new ProtoCodec<>() {
        @Override
        public Long decode(CodedInputStream in) throws IOException {
            long v = 0L;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 8) v = in.readInt64(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Long value, CodedOutputStream out) throws IOException {
            if (value != 0L) out.writeInt64(1, value);
        }

        @Override
        public int computeSize(Long value) {
            return value != 0L ? CodedOutputStream.computeInt64Size(1, value) : 0;
        }
    };

    public static final ProtoCodec<Long> UINT64_VALUE = new ProtoCodec<>() {
        @Override
        public Long decode(CodedInputStream in) throws IOException {
            long v = 0L;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 8) v = in.readUInt64(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Long value, CodedOutputStream out) throws IOException {
            if (value != 0L) out.writeUInt64(1, value);
        }

        @Override
        public int computeSize(Long value) {
            return value != 0L ? CodedOutputStream.computeUInt64Size(1, value) : 0;
        }
    };

    public static final ProtoCodec<Integer> INT32_VALUE = new ProtoCodec<>() {
        @Override
        public Integer decode(CodedInputStream in) throws IOException {
            int v = 0;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 8) v = in.readInt32(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Integer value, CodedOutputStream out) throws IOException {
            if (value != 0) out.writeInt32(1, value);
        }

        @Override
        public int computeSize(Integer value) {
            return value != 0 ? CodedOutputStream.computeInt32Size(1, value) : 0;
        }
    };

    public static final ProtoCodec<Integer> UINT32_VALUE = new ProtoCodec<>() {
        @Override
        public Integer decode(CodedInputStream in) throws IOException {
            int v = 0;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 8) v = in.readUInt32(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Integer value, CodedOutputStream out) throws IOException {
            if (value != 0) out.writeUInt32(1, value);
        }

        @Override
        public int computeSize(Integer value) {
            return value != 0 ? CodedOutputStream.computeUInt32Size(1, value) : 0;
        }
    };

    public static final ProtoCodec<Boolean> BOOL_VALUE = new ProtoCodec<>() {
        @Override
        public Boolean decode(CodedInputStream in) throws IOException {
            boolean v = false;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 8) v = in.readBool(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(Boolean value, CodedOutputStream out) throws IOException {
            if (value) out.writeBool(1, true);
        }

        @Override
        public int computeSize(Boolean value) {
            return value ? CodedOutputStream.computeBoolSize(1, true) : 0;
        }
    };

    public static final ProtoCodec<String> STRING_VALUE = new ProtoCodec<>() {
        @Override
        public String decode(CodedInputStream in) throws IOException {
            String v = "";
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 10) v = in.readStringRequireUtf8(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(String value, CodedOutputStream out) throws IOException {
            if (!value.isEmpty()) out.writeString(1, value);
        }

        @Override
        public int computeSize(String value) {
            return value.isEmpty() ? 0 : CodedOutputStream.computeStringSize(1, value);
        }
    };

    public static final ProtoCodec<ByteBuffer> BYTE_BUFFER_VALUE = new ProtoCodec<>() {
        @Override
        public ByteBuffer decode(CodedInputStream in) throws IOException {
            ByteBuffer v = Wire.EMPTY_BUFFER;
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 10) v = Wire.readByteBuffer(in); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(ByteBuffer value, CodedOutputStream out) throws IOException {
            if (value.hasRemaining()) Wire.writeByteBuffer(out, 1, value);
        }

        @Override
        public int computeSize(ByteBuffer value) {
            return value.hasRemaining() ? Wire.computeByteBufferSize(1, value) : 0;
        }
    };

    public static final ProtoCodec<byte[]> BYTE_ARRAY_VALUE = new ProtoCodec<>() {
        @Override
        public byte[] decode(CodedInputStream in) throws IOException {
            byte[] v = new byte[0];
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (tag == 10) v = in.readByteArray(); else in.skipField(tag);
            }
            return v;
        }

        @Override
        public void encode(byte[] value, CodedOutputStream out) throws IOException {
            if (value.length != 0) out.writeByteArray(1, value);
        }

        @Override
        public int computeSize(byte[] value) {
            return value.length == 0 ? 0 : CodedOutputStream.computeByteArraySize(1, value);
        }
    };
}
