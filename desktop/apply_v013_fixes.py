from pathlib import Path

path = Path(__file__).with_name("getmuvi_windows.py")
text = path.read_text(encoding="utf-8")

replacements = {
    '        self.geometry("1040x760")\n        self.minsize(900, 700)':
        '        self.geometry("1040x720")\n        self.minsize(860, 620)',

    '        self.content.grid_rowconfigure(4, weight=1)':
        '        self.content.grid_rowconfigure(4, weight=0)',

    '        self.initial_frame.grid(row=4, column=0, sticky="nsew")':
        '        self.initial_frame.grid(row=4, column=0, sticky="ew")',

    '        self.details.grid(row=4, column=0, sticky="nsew")':
        '        self.details.grid(row=4, column=0, sticky="ew")',

    '        self.details.grid_rowconfigure(3, weight=1)':
        '        self.details.grid_rowconfigure(3, weight=0)',

    '        self.action_card.grid(row=3, column=0, sticky="nsew")':
        '        self.action_card.grid(row=3, column=0, sticky="ew")',

    '        self.details.grid_remove()\n        self.success_card.grid_remove()\n        self.error_card.grid_remove()\n        self.initial_frame.grid()':
        '        self.details.grid_remove()\n        self.success_card.grid_remove()\n        self.error_card.grid_remove()\n        self.content.grid_rowconfigure(4, weight=1)\n        self.initial_frame.grid(sticky="nsew")',

    '        self.initial_frame.grid_remove()\n        self.success_card.grid_remove()\n        self.error_card.grid_remove()\n        self.details.grid()':
        '        self.initial_frame.grid_remove()\n        self.success_card.grid_remove()\n        self.error_card.grid_remove()\n        self.content.grid_rowconfigure(4, weight=0)\n        self.details.grid(sticky="ew")',

    '            self.details.grid_remove()\n            self.initial_frame.grid()':
        '            self.details.grid_remove()\n            self.content.grid_rowconfigure(4, weight=1)\n            self.initial_frame.grid(sticky="nsew")',
}

missing = []
for old, new in replacements.items():
    if old not in text:
        missing.append(old)
    else:
        text = text.replace(old, new, 1)

if missing:
    raise SystemExit("Trechos esperados não encontrados no patch 0.1.3:\n\n" + "\n---\n".join(missing))

# Compacta ligeiramente o espaçamento vertical sem alterar a identidade visual.
text = text.replace('header.grid(row=0, column=0, sticky="ew", padx=26, pady=(14, 5))',
                    'header.grid(row=0, column=0, sticky="ew", padx=26, pady=(10, 3))', 1)
text = text.replace('self.content.grid(row=1, column=0, sticky="nsew", padx=26, pady=(2, 18))',
                    'self.content.grid(row=1, column=0, sticky="nsew", padx=26, pady=(0, 12))', 1)

path.write_text(text, encoding="utf-8")
print("GetMuvi Windows 0.1.3: layout compacto aplicado.")
