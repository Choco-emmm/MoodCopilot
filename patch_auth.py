import os

p = "backend/moodcopilot/src/main/java/com/moodcopilot/auth/AuthService.java"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = '^[a-zA-Z0-9\\\\u4e00-\\\\u9fa5_-]{2,20}$'
replacement = '^[a-zA-Z0-9\\\\u4E00-\\\\u9FA5_-]{2,20}$'
# Wait! In Java, regex "\\u4e00" is also valid because Regex engine parses \uXXXX. 
# Oh, so "\\\\u4e00" is sent to the Pattern as "\u4e00", which the regex engine interprets as Unicode!
# Wait, let's verify if Pattern.compile("^...\\u4e00...$") works.
# Actually, wait... In Java, \u4e00 evaluates to the character at compile time. 
# \\u4e00 evaluates to \u4e00 string, which the Java Regex engine ALSO interprets as unicode.
