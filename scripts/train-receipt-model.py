"""Trains the on-device receipt line classifier that ships in the APK.

    py scripts/train-receipt-model.py

Reads scripts/receipt-corpus.txt (hand written slips, one blank line between
them, every line prefixed with its label), multiplies it with generated slips
and OCR noise, fits a softmax regression over hashed features and writes
app/src/main/assets/receipt-model.txt.

The feature extraction below MUST stay identical to LineFeatures.kt — the test
ReceiptModelTest keeps both honest.

Labels: T total, I item (a line naming a purchase), M merchant, O other.
"""

import os
import random
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORPUS = os.path.join(ROOT, "scripts", "receipt-corpus.txt")
TARGET = os.path.join(ROOT, "app", "src", "main", "assets", "receipt-model.txt")

DENSE = 16
TOKEN_BUCKETS = 2048
GRAM_BUCKETS = 4096
TOKEN_BASE = 1 + DENSE
GRAM_BASE = TOKEN_BASE + TOKEN_BUCKETS
SIZE = GRAM_BASE + GRAM_BUCKETS

CLASSES = ["TOTAL", "ITEM", "MERCHANT", "OTHER"]
LABELS = {"T": 0, "I": 1, "M": 2, "O": 3}

AMOUNT = re.compile(r"(?<!\d)\d{1,9}(?:[  ]\d{3})*[.,]\d{2}(?!\d)")
QUANTITY = re.compile(r"[\dхx×*]\s*[хx×*]\s*\d")
SPACES = re.compile(r"\s+")
CURRENCY = ["byn", "руб", "р.", "br", "бр", "eur", "usd"]


def fnv(text):
    value = 2166136261
    for byte in text.encode("utf-8"):
        value = ((value ^ byte) * 16777619) & 0xFFFFFFFF
    return value & 0x7FFFFFFF


def normalized(text):
    shaped = "".join("#" if "0" <= ch <= "9" else ch for ch in text.lower())
    return SPACES.sub(" ", shaped).strip()


def tokens(shape):
    out, current = [], []
    for ch in shape:
        if ch.isalpha() or ch == "#":
            current.append(ch)
        else:
            if len(current) >= 2:
                out.append("".join(current))
            current = []
    if len(current) >= 2:
        out.append("".join(current))
    return out[:24]


def grams(shape):
    if len(shape) < 3:
        return []
    padded = " " + shape + " "
    return [padded[i:i + 3] for i in range(len(padded) - 2)][:96]


def features(text, emphasis, index, count):
    shape = normalized(text)
    weights = {0: 1.0}

    letters = sum(1 for ch in text if ch.isalpha())
    digits = sum(1 for ch in text if "0" <= ch <= "9")
    upper = sum(1 for ch in text if ch.isupper())
    found = AMOUNT.findall(text)
    lower = text.lower()
    span = float(max(len(text), 1))
    position = index / (count - 1) if count > 1 else 0.0
    tail = AMOUNT.search(text)

    dense = [
        1.0 if found else 0.0,
        min(len(found) / 3.0, 1.0),
        digits / span,
        letters / span,
        upper / letters if letters else 0.0,
        min(len(text) / 40.0, 1.0),
        position,
        position * position,
        1.0 if index < 3 else 0.0,
        1.0 if index >= count - 3 else 0.0,
        min(max(emphasis - 1.0, 0.0), 1.0),
        1.0 if ":" in text else 0.0,
        1.0 if "%" in text else 0.0,
        1.0 if QUANTITY.search(lower) else 0.0,
        1.0 if any(word in lower for word in CURRENCY) else 0.0,
        1.0 if tail and text.rstrip() == text and tail.end() == len(text) else 0.0,
    ]
    # The last dense feature asks whether the line ends with the amount; the
    # regex has to be re-run because findall drops positions.
    ends = 0.0
    for match in AMOUNT.finditer(text):
        if match.end() == len(text):
            ends = 1.0
    dense[15] = ends

    for at, value in enumerate(dense):
        if value != 0.0:
            weights[1 + at] = value

    for token in tokens(shape):
        slot = TOKEN_BASE + fnv(token) % TOKEN_BUCKETS
        weights[slot] = weights.get(slot, 0.0) + 1.0
    for gram in grams(shape):
        slot = GRAM_BASE + fnv(gram) % GRAM_BUCKETS
        weights[slot] = weights.get(slot, 0.0) + 1.0

    return sorted(weights.items())


SHOPS = [
    ('ООО "Евроопт"', "евроопт"), ('ЗАО "Санта Ритейл"', "санта"),
    ("ОАО Гиппо", "гиппо"), ('ООО "Грин Ритейл"', "green"),
    ('ЧУП "Копеечка"', "копеечка"), ('ООО "Виталюр"', "виталюр"),
    ('ОДО "Соседи"', "соседи"), ('ООО "Белмаркет"', "белмаркет"),
    ('ИП Ковалёв А.А.', "ковалёв"), ('ООО "Остров чистоты"', "остров"),
    ('ТЧУП "Доброном"', "доброном"), ('ООО "Мила"', "мила"),
    ('ООО "5 элемент"', "5 элемент"), ('ЗАО "Электросила"', "электросила"),
    ('ООО "Буслік"', "буслік"), ("Аптека №14", "аптека"),
    ('ООО "Прима Тэйст"', "прима"), ('ЧТУП "Кофейня на Немиге"', "кофейня"),
]

GOODS = [
    "Молоко Савушкин 3,2% 1л", "Хлеб Нарочанский", "Сыр Тильзитер 45%",
    "Батон нарезной", "Яйцо С1 10шт", "Масло сливочное 82,5%",
    "Кефир 2,5% 0,9л", "Сметана 20% 350г", "Творог 5% 200г",
    "Бананы", "Яблоки Джонаголд", "Картофель мытый", "Морковь",
    "Лук репчатый", "Огурцы гладкие", "Помидоры черри",
    "Кофе Jacobs Monarch 190г", "Чай Curtis 25п", "Сахар-песок 1кг",
    "Мука пшеничная в/с", "Макароны Спагетти", "Рис длиннозёрный",
    "Гречка ядрица 800г", "Курица бройлер охл.", "Фарш домашний",
    "Колбаса Докторская", "Сосиски Молочные", "Печенье Юбилейное",
    "Шоколад Спартак 90г", "Вода Дарида 1,5л", "Сок Сочный фрукт 1л",
    "Пакет майка", "Салфетки бумажные", "Порошок стиральный 3кг",
    "Мыло жидкое 500мл", "Зубная паста Colgate", "Шампунь Head&Shoulders",
    "Парацетамол 500мг №10", "Бинт стерильный", "Лампа LED 9Вт",
    "Батарейки АА 4шт", "Кабель USB Type-C", "Наушники TWS",
    "Americano 300ml", "Cappuccino", "Круассан с миндалём",
]

STREETS = [
    "ул. Притыцкого, 29", "пр-т Независимости, 58", "ул. Немига, 5",
    "ул. Кульман, 1", "пр-т Дзержинского, 104", "ул. Сурганова, 57Б",
]

CASHIERS = ["Иванова М.П.", "Петров А.С.", "Сидорова Е.В.", "Веремей Ж.Н."]

TOTAL_WORDS = ["ИТОГО К ОПЛАТЕ", "К ОПЛАТЕ", "ИТОГО", "ВСЕГО", "СУМА", "УСЯГО"]
PAY_WORDS = ["ОПЛАТА КАРТОЙ", "БЕЗНАЛИЧНЫМИ", "НАЛИЧНЫМИ", "ОПЛАТА,VISA/MC", "БЕЛКАРТ"]


def money(low, high):
    return f"{random.randint(low, high)},{random.randint(0, 99):02d}"


WRAPS = [
    "полим/уп", "флоу-пак", "в/к охл вес 1 кг в/уп", "пэт/бут", "ваф стак 70 г",
    "домашнему 85 г флоу-пак", "с яйцом и зеленью 180 г",
]


def wrapped(name, rng):
    if rng.random() < 0.45:
        return name, None
    return name, rng.choice(WRAPS)


def synthetic(rng):
    shop, _ = rng.choice(SHOPS)
    lines = []
    if rng.random() < 0.3:
        lines.append(("O", "КАССОВЫЙ ЧЕК"))
    lines.append(("M", shop))
    lines.append(("O", f"УНП {rng.randint(100000000, 999999999)}"))
    lines.append(("O", rng.choice(STREETS)))
    if rng.random() < 0.5:
        lines.append(("O", f"Магазин №{rng.randint(1, 900)}  Смена {rng.randint(1, 9)}"))
    lines.append(("O", f"Кассир: {rng.choice(CASHIERS)}"))
    lines.append((
        "O",
        f"{rng.randint(1, 28):02d}.{rng.randint(1, 12):02d}.2026 "
        f"{rng.randint(8, 22)}:{rng.randint(0, 59):02d}",
    ))
    if rng.random() < 0.6:
        lines.append(("O", "Наименование      Кол-во   Цена   Стоимость"))

    total = 0
    ordinal = 0
    for _ in range(rng.randint(2, 9)):
        name = rng.choice(GOODS)
        price = money(0, 40)
        style = rng.random()
        if style < 0.4:
            total += int(price.replace(",", ""))
            lines.append(("I", f"{name}   {price}"))
        elif style < 0.58:
            total += int(price.replace(",", ""))
            lines.append(("I", name))
            count = f"{rng.randint(1, 3)},{rng.randint(0, 999):03d}"
            lines.append(("O", f"{rng.randint(1000, 99999)}  шт*{count}  {price}  {price}"))
        elif style < 0.7:
            total += int(price.replace(",", ""))
            lines.append(("I", f"{name}  {rng.randint(1, 4)}x{money(0, 9)}  {price}"))
        elif style < 0.88:
            ordinal += 1
            head, tail = wrapped(name, rng)
            lines.append(("I", f"{ordinal}. {head}"))
            if tail:
                lines.append(("I", tail))
            unit = int(price.replace(",", ""))
            count = rng.choice(["1.000", "1.000", "2.000", "0.555", "0.742", "1.214"])
            line_total = round(unit * float(count))
            total += line_total
            gap = " " * rng.randint(4, 18)
            shown = f"{line_total // 100}.{line_total % 100:02d}"
            lines.append(("O", f"{price.replace(',', '.')} × {count}{gap}{shown}"))
        else:
            total += int(price.replace(",", ""))
            lines.append(("I", f"{rng.randint(1000000, 9999999)} {name}"))
            lines.append(("O", f"{rng.randint(1000000000000, 9999999999999)}"))
            shown = price.replace(",", ".")
            lines.append(("O", f"        {shown}      x1.000       {shown}"))

    shown = f"{total // 100},{total % 100:02d}"
    lines.append(("T", f"{rng.choice(TOTAL_WORDS)}: {shown}"))
    if rng.random() < 0.7:
        lines.append(("O", f"НДС {rng.choice([10, 20])}%   {money(0, 9)}"))
    if rng.random() < 0.8:
        lines.append(("O", f"{rng.choice(PAY_WORDS)}   {shown}"))
    if rng.random() < 0.4:
        lines.append(("O", f"СДАЧА   {money(0, 2)}"))
    if rng.random() < 0.5:
        lines.append(("O", rng.choice(["СПАСИБО ЗА ПОКУПКУ!", "ДЗЯКУЙ ЗА ПАКУПКУ!"])))
    if rng.random() < 0.4:
        lines.append(("O", f"СКНО {rng.randint(1000000, 9999999)}"))
    return lines


CONFUSIONS = {
    "о": "0", "О": "0", "з": "3", "З": "3", "б": "6", "l": "1", "I": "1",
    "и": "н", "ш": "щ", "с": "c", "е": "e", "а": "a", "у": "y", "х": "x",
    "0": "О", "1": "l", "5": "S", "8": "В",
}


def noisy(text, rng, rate):
    out = []
    for ch in text:
        roll = rng.random()
        if roll < rate and ch in CONFUSIONS:
            out.append(CONFUSIONS[ch])
        elif roll < rate * 1.3 and ch == " ":
            out.append("  ")
        elif roll < rate * 1.4:
            continue
        else:
            out.append(ch)
    return "".join(out)


def read_corpus():
    if not os.path.exists(CORPUS):
        return []
    slips, current = [], []
    with open(CORPUS, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.rstrip("\n")
            if not line.strip():
                if current:
                    slips.append(current)
                    current = []
                continue
            if line.startswith("#"):
                continue
            label, _, text = line.partition("\t")
            if label.strip() in LABELS and text.strip():
                current.append((label.strip(), text))
    if current:
        slips.append(current)
    return slips


def samples(slips, rng, noise_rounds):
    rows = []
    for slip in slips:
        variants = [slip]
        for round_index in range(noise_rounds):
            rate = 0.02 + 0.03 * round_index
            variants.append([(label, noisy(text, rng, rate)) for label, text in slip])
        for variant in variants:
            count = len(variant)
            for index, (label, text) in enumerate(variant):
                emphasis = 1.5 if label == "M" and rng.random() < 0.4 else 1.0
                rows.append((features(text, emphasis, index, count), LABELS[label]))
    return rows


def train(rows, epochs, learning_rate, decay, rng):
    weights = [[0.0] * SIZE for _ in CLASSES]
    order = list(range(len(rows)))
    for epoch in range(epochs):
        rng.shuffle(order)
        step = learning_rate / (1.0 + epoch)
        for at in order:
            vector, target = rows[at]
            scores = []
            for row in weights:
                scores.append(sum(row[i] * v for i, v in vector))
            top = max(scores)
            exps = [pow(2.718281828459045, s - top) for s in scores]
            norm = sum(exps)
            for label, row in enumerate(weights):
                error = exps[label] / norm - (1.0 if label == target else 0.0)
                if error == 0.0:
                    continue
                for i, v in vector:
                    row[i] -= step * (error * v + decay * row[i])
        print(f"  epoch {epoch + 1}/{epochs} done", flush=True)
    return weights


def accuracy(weights, rows):
    hits = 0
    confusion = [[0] * len(CLASSES) for _ in CLASSES]
    for vector, target in rows:
        scores = [sum(row[i] * v for i, v in vector) for row in weights]
        guess = scores.index(max(scores))
        confusion[target][guess] += 1
        if guess == target:
            hits += 1
    return hits / max(len(rows), 1), confusion


def write(weights):
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("kosht-receipt-model 1\n")
        handle.write(f"size {SIZE}\n")
        for name, row in zip(CLASSES, weights):
            pairs = [
                f"{i}:{round(w, 4)}" for i, w in enumerate(row) if abs(w) >= 0.01
            ]
            handle.write(name + " " + " ".join(pairs) + "\n")
    return os.path.getsize(TARGET)


def main():
    rng = random.Random(20260726)
    handwritten = read_corpus()
    generated = [synthetic(rng) for _ in range(220)]
    # Three written slips never reach training, so the score below is what the
    # model does with layouts it has not seen.
    unseen = handwritten[-3:]
    taught = handwritten[:-3]
    print(f"slips: {len(taught)} written + {len(generated)} generated, {len(unseen)} held back")

    rows = samples(taught + generated, rng, noise_rounds=2)
    checks = samples(unseen + [synthetic(rng) for _ in range(40)], rng, noise_rounds=1)
    print(f"lines: {len(rows)} training, {len(checks)} held out")

    weights = train(rows, epochs=6, learning_rate=0.25, decay=1e-6, rng=rng)
    score, confusion = accuracy(weights, checks)
    print(f"held-out accuracy: {score:.3f}")
    print("       " + "".join(f"{name[:4]:>7}" for name in CLASSES))
    for name, row in zip(CLASSES, confusion):
        print(f"{name[:6]:>6} " + "".join(f"{value:>7}" for value in row))

    size = write(weights)
    print(f"wrote {TARGET} ({size // 1024} KB)")
    return 0 if score > 0.9 else 1


if __name__ == "__main__":
    sys.exit(main())
