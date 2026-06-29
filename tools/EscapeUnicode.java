import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

public class EscapeUnicode {
    public static void main(String[] args) throws Exception {
        for (String f : new String[]{
            "src/gj-plugin-demo/src/main/resources/i18n/messages.properties",
            "src/gj-plugin-demo/src/main/resources/i18n/messages_zh_CN.properties"
        }) {
            String s = Files.readString(Path.of(f), StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c > 127) sb.append(String.format("\u%04X", (int) c));
                else sb.append(c);
            }
            Files.writeString(Path.of(f), sb.toString(), StandardCharsets.ISO_8859_1);
            System.out.println("OK: " + f);
        }
    }
}
