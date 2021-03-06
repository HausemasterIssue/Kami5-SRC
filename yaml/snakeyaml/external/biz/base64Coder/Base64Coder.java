package org.yaml.snakeyaml.external.biz.base64Coder;

public class Base64Coder {

    private static final String systemLineSeparator = System.getProperty("line.separator");
    private static char[] map1 = new char[64];
    private static byte[] map2;

    public static String encodeString(String s) {
        return new String(encode(s.getBytes()));
    }

    public static String encodeLines(byte[] in) {
        return encodeLines(in, 0, in.length, 76, Base64Coder.systemLineSeparator);
    }

    public static String encodeLines(byte[] in, int iOff, int iLen, int lineLen, String lineSeparator) {
        int blockLen = lineLen * 3 / 4;

        if (blockLen <= 0) {
            throw new IllegalArgumentException();
        } else {
            int lines = (iLen + blockLen - 1) / blockLen;
            int bufLen = (iLen + 2) / 3 * 4 + lines * lineSeparator.length();
            StringBuilder buf = new StringBuilder(bufLen);

            int l;

            for (int ip = 0; ip < iLen; ip += l) {
                l = Math.min(iLen - ip, blockLen);
                buf.append(encode(in, iOff + ip, l));
                buf.append(lineSeparator);
            }

            return buf.toString();
        }
    }

    public static char[] encode(byte[] in) {
        return encode(in, 0, in.length);
    }

    public static char[] encode(byte[] in, int iLen) {
        return encode(in, 0, iLen);
    }

    public static char[] encode(byte[] in, int iOff, int iLen) {
        int oDataLen = (iLen * 4 + 2) / 3;
        int oLen = (iLen + 2) / 3 * 4;
        char[] out = new char[oLen];
        int ip = iOff;
        int iEnd = iOff + iLen;

        for (int op = 0; ip < iEnd; ++op) {
            int i0 = in[ip++] & 255;
            int i1 = ip < iEnd ? in[ip++] & 255 : 0;
            int i2 = ip < iEnd ? in[ip++] & 255 : 0;
            int o0 = i0 >>> 2;
            int o1 = (i0 & 3) << 4 | i1 >>> 4;
            int o2 = (i1 & 15) << 2 | i2 >>> 6;
            int o3 = i2 & 63;

            out[op++] = Base64Coder.map1[o0];
            out[op++] = Base64Coder.map1[o1];
            out[op] = op < oDataLen ? Base64Coder.map1[o2] : 61;
            ++op;
            out[op] = op < oDataLen ? Base64Coder.map1[o3] : 61;
        }

        return out;
    }

    public static String decodeString(String s) {
        return new String(decode(s));
    }

    public static byte[] decodeLines(String s) {
        char[] buf = new char[s.length()];
        int p = 0;

        for (int ip = 0; ip < s.length(); ++ip) {
            char c = s.charAt(ip);

            if (c != 32 && c != 13 && c != 10 && c != 9) {
                buf[p++] = c;
            }
        }

        return decode(buf, 0, p);
    }

    public static byte[] decode(String s) {
        return decode(s.toCharArray());
    }

    public static byte[] decode(char[] in) {
        return decode(in, 0, in.length);
    }

    public static byte[] decode(char[] in, int iOff, int iLen) {
        if (iLen % 4 != 0) {
            throw new IllegalArgumentException("Length of Base64 encoded input string is not a multiple of 4.");
        } else {
            while (iLen > 0 && in[iOff + iLen - 1] == 61) {
                --iLen;
            }

            int oLen = iLen * 3 / 4;
            byte[] out = new byte[oLen];
            int ip = iOff;
            int iEnd = iOff + iLen;
            int op = 0;

            while (ip < iEnd) {
                char i0 = in[ip++];
                char i1 = in[ip++];
                char i2 = ip < iEnd ? in[ip++] : 65;
                char i3 = ip < iEnd ? in[ip++] : 65;

                if (i0 <= 127 && i1 <= 127 && i2 <= 127 && i3 <= 127) {
                    byte b0 = Base64Coder.map2[i0];
                    byte b1 = Base64Coder.map2[i1];
                    byte b2 = Base64Coder.map2[i2];
                    byte b3 = Base64Coder.map2[i3];

                    if (b0 >= 0 && b1 >= 0 && b2 >= 0 && b3 >= 0) {
                        int o0 = b0 << 2 | b1 >>> 4;
                        int o1 = (b1 & 15) << 4 | b2 >>> 2;
                        int o2 = (b2 & 3) << 6 | b3;

                        out[op++] = (byte) o0;
                        if (op < oLen) {
                            out[op++] = (byte) o1;
                        }

                        if (op < oLen) {
                            out[op++] = (byte) o2;
                        }
                        continue;
                    }

                    throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
                }

                throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
            }

            return out;
        }
    }

    static {
        int i = 0;

        char c;

        for (c = 65; c <= 90; Base64Coder.map1[i++] = c++) {
            ;
        }

        for (c = 97; c <= 122; Base64Coder.map1[i++] = c++) {
            ;
        }

        for (c = 48; c <= 57; Base64Coder.map1[i++] = c++) {
            ;
        }

        Base64Coder.map1[i++] = 43;
        Base64Coder.map1[i++] = 47;
        Base64Coder.map2 = new byte[128];

        for (i = 0; i < Base64Coder.map2.length; ++i) {
            Base64Coder.map2[i] = -1;
        }

        for (i = 0; i < 64; ++i) {
            Base64Coder.map2[Base64Coder.map1[i]] = (byte) i;
        }

    }
}
