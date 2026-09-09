import java.util.regex.Pattern;
public class TestRegex {
    public static void main(String[] args) {
        String chinese = "你好";
        System.out.println("With double backslash: " + chinese.matches("^[a-zA-Z0-9\\\\u4e00-\\\\u9fa5_-]{2,20}$"));
        System.out.println("With single backslash: " + chinese.matches("^[a-zA-Z0-9\u4e00-\u9fa5_-]{2,20}$"));
    }
}
