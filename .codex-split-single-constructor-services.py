from pathlib import Path
import re

FILES = [
    "vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentAdmissionCoordinator.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentServiceImpl.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/shop/payment/SimulatedPaymentService.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/CheckoutService.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ApplicationSchemaInitializer.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/student/service/StudentProfileServiceImpl.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/library/service/LibraryServiceImpl.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/AdminProductService.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/governance/ModuleAdministrationService.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerApplicationService.java",
    "vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminService.java",
]


def scan(text):
    state, escaped, i = "code", False, 0
    while i < len(text):
        char, nxt = text[i], text[i + 1] if i + 1 < len(text) else ""
        yield i, char, state
        if state == "code":
            if char == '/' and nxt == '/': state = "line"
            elif char == '/' and nxt == '*': state = "block"
            elif char == '"': state = "string"
            elif char == "'": state = "char"
        elif state == "line" and char == '\n': state = "code"
        elif state == "block" and char == '*' and nxt == '/': state = "block_end"
        elif state == "block_end": state = "code"
        elif state in ("string", "char"):
            if escaped: escaped = False
            elif char == '\\': escaped = True
            elif (state == "string" and char == '"') or (state == "char" and char == "'"): state = "code"
        i += 1


def split_params(value):
    parts, start, depth = [], 0, 0
    for i, char in enumerate(value):
        if char in "<([": depth += 1
        elif char in ">)]": depth = max(0, depth - 1)
        elif char == ',' and depth == 0:
            parts.append(value[start:i]); start = i + 1
    parts.append(value[start:])
    return parts


for filename in FILES:
    path = Path(filename)
    source = path.read_text(encoding="utf-8")
    name = path.stem
    class_match = re.search(rf"public final class {name}([^\{{]*)\{{", source)
    inheritance = class_match.group(1).strip()
    prefix = source[:class_match.start()]
    class_doc = prefix.rfind("/**")
    header = prefix[:class_doc]
    body_start = class_match.end()
    depth = 1
    for i, char, state in scan(source[body_start:]):
        if state != "code": continue
        if char == '{': depth += 1
        elif char == '}':
            depth -= 1
            if depth == 0:
                body = source[body_start:body_start + i]
                break
    members, start, depth = [], 0, 0
    for i, char, state in scan(body):
        if state != "code": continue
        if char == '{': depth += 1
        elif char == '}':
            depth -= 1
            if depth == 0:
                look = i + 1
                while look < len(body) and body[look].isspace(): look += 1
                if look >= len(body) or body[look] != ';':
                    members.append(body[start:i + 1]); start = i + 1
        elif char == ';' and depth == 0:
            members.append(body[start:i + 1]); start = i + 1

    fields, constructor, helpers, operations = [], None, [], []
    for member in members:
        signature = member.strip().split('{', 1)[0]
        if not member.strip(): continue
        if re.search(rf"\b{name}\s*\(", signature): constructor = member
        elif re.search(r"\bpublic\b|\bprotected\b", signature) and '(' in signature:
            operations.append(member)
        else:
            normalized = member.replace("private static", "protected static").replace(
                "private final", "protected final").replace("private ", "protected ", 1)
            if '(' in signature: helpers.append(normalized)
            else: fields.append(normalized)
    if constructor is None:
        raise RuntimeError(f"Expected one constructor in {name}")

    constructor_head = constructor.split('{', 1)[0]
    params = constructor_head.split('(', 1)[1].rsplit(')', 1)[0]
    arguments = []
    for part in split_params(params):
        ids = re.findall(r"[A-Za-z_$][\w$]*", part)
        if ids: arguments.append(ids[-1])
    args = ", ".join(arguments)
    throws = ""
    throws_match = re.search(r"\)\s*(throws\s+[^\{]+)", constructor_head)
    if throws_match: throws = " " + throws_match.group(1).strip()

    base = f"{name}Support"
    root_ctor = constructor.replace(f"public {name}", f"protected {base}", 1)
    suffix = " " + inheritance if inheritance else ""
    text = header + f"/** Holds dependencies and shared state for {{@link {name}}}. */\n"
    text += f"abstract class {base}{suffix} {{" + "".join(fields) + root_ctor + "\n}\n"
    (path.parent / f"{base}.java").write_text(text, encoding="utf-8", newline="\n")

    def pass_ctor(class_name):
        return (f"\n    protected {class_name}({params}){throws} {{\n"
                f"        super({args});\n    }}\n")

    def chunks(values, limit=85):
        result, current, count = [], [], 0
        for member in values:
            size = member.count('\n') + 1
            if current and count + size > limit:
                result.append(current); current, count = [], 0
            current.append(member); count += size
        if current: result.append(current)
        return result

    previous = base
    helper_chunks = chunks(helpers)
    for zero in reversed(range(len(helper_chunks))):
        segment = f"{name}Helpers{zero + 1}"
        text = header + f"/** Provides focused helper operations for {{@link {name}}}. */\n"
        text += f"abstract class {segment} extends {previous} {{" + pass_ctor(segment)
        text += "".join(helper_chunks[zero]) + "\n}\n"
        (path.parent / f"{segment}.java").write_text(text, encoding="utf-8", newline="\n")
        previous = segment

    operation_chunks = chunks(operations)
    for zero in reversed(range(len(operation_chunks))):
        segment = f"{name}Operations{zero + 1}"
        text = header + f"/** Implements focused public operations for {{@link {name}}}. */\n"
        text += f"abstract class {segment} extends {previous} {{" + pass_ctor(segment)
        text += "".join(operation_chunks[zero]) + "\n}\n"
        (path.parent / f"{segment}.java").write_text(text, encoding="utf-8", newline="\n")
        previous = segment

    first = f"{name}Operations1" if operation_chunks else (
        f"{name}Helpers1" if helper_chunks else base)
    facade = header + f"/** Public compatibility facade for {name}. */\n"
    facade += f"public final class {name} extends {first} {{\n"
    facade += constructor.replace(f"public {name}", f"public {name}", 1).split('{', 1)[0]
    facade += f"{{\n        super({args});\n    }}\n}}\n"
    path.write_text(facade, encoding="utf-8", newline="\n")
    print(name, len(helper_chunks), len(operation_chunks))
