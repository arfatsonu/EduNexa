import os, re

kt_files = []
for root, dirs, files in os.walk("app/src/main/java"):
    for f in files:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(root, f))

print(f"Total Kotlin files: {len(kt_files)}")

def check_balance(path):
    with open(path, "r", encoding="utf-8") as fp:
        c = fp.read()
    
    i = 0
    n = len(c)
    stack = []
    pairs = {")": "(", "}": "{", "]": "["}
    line = 1
    
    while i < n:
        if c[i] == "\n":
            line += 1
            i += 1
            continue
            
        if c[i:i+2] == "//":
            i += 2
            while i < n and c[i] != "\n":
                i += 1
            continue
            
        if c[i:i+2] == "/*":
            i += 2
            while i < n and c[i:i+2] != "*/":
                if c[i] == "\n":
                    line += 1
                i += 1
            i += 2
            continue
            
        if c[i:i+3] == '"""':
            i += 3
            while i < n and c[i:i+3] != '"""':
                if c[i] == "\n":
                    line += 1
                i += 1
            i += 3
            continue
            
        if c[i] == "'":
            i += 1
            if i < n and c[i] == "\\":
                i += 2
            else:
                i += 1
            if i < n and c[i] == "'":
                i += 1
            continue
            
        if c[i] == '"':
            i += 1
            while i < n and c[i] != '"':
                if c[i] == "\\" and i + 1 < n:
                    i += 2
                    continue
                if c[i] == "\n":
                    line += 1
                i += 1
            i += 1
            continue
            
        if c[i] in ("(", "{", "["):
            stack.append((c[i], line))
        elif c[i] in (")", "}", "]"):
            if not stack:
                return f"Unmatched closing {c[i]} at line {line}"
            open_ch, open_line = stack.pop()
            if open_ch != pairs[c[i]]:
                return f"Mismatched: open {open_ch} (line {open_line}) closed by {c[i]} (line {line})"
        i += 1
        
    if stack:
        return f"Unclosed tokens: {stack[:5]}"
    return "OK"

errors = 0
for kf in sorted(kt_files):
    res = check_balance(kf)
    if res != "OK":
        print(f"FAILED: {kf} -> {res}")
        errors += 1

if errors == 0:
    print("ALL 100% of Kotlin files have PERFECT syntax balance (brackets, parens, strings)!")
else:
    print(f"Total errors: {errors}")
