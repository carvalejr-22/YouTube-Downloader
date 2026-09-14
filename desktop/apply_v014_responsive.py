from pathlib import Path

path = Path(__file__).with_name("getmuvi_windows.py")
text = path.read_text(encoding="utf-8")

# O patch 0.1.4 roda DEPOIS do 0.1.3 no workflow. Ele transforma a área
# analisada em um layout desktop de duas colunas, eliminando qualquer
# espaço elástico entre o botão Analisar e as opções de download.

replacements = {
    '        controls = ctk.CTkFrame(self.details, fg_color=SURFACE, corner_radius=18)':
        '        self.controls_card = ctk.CTkFrame(self.details, fg_color=SURFACE, corner_radius=18)',
    '        controls.grid(row=1, column=0, sticky="ew", pady=(0, 8))':
        '        self.controls_card.grid(row=1, column=0, sticky="ew", pady=(0, 8))',
    '        controls.grid_columnconfigure(0, weight=1)':
        '        self.controls_card.grid_columnconfigure(0, weight=1)',
    '        ctk.CTkLabel(controls, text="O que você quer baixar?", text_color=TEXT, font=ctk.CTkFont(size=13, weight="bold")).grid(row=0, column=0, sticky="w", padx=15, pady=(11, 6))':
        '        ctk.CTkLabel(self.controls_card, text="O que você quer baixar?", text_color=TEXT, font=ctk.CTkFont(size=13, weight="bold")).grid(row=0, column=0, sticky="w", padx=15, pady=(11, 6))',
    '        self.kind_row = ctk.CTkFrame(controls, fg_color="transparent")':
        '        self.kind_row = ctk.CTkFrame(self.controls_card, fg_color="transparent")',
    '        self.option_caption = ctk.CTkLabel(controls, text="Qualidade e formato", text_color=MUTED, font=ctk.CTkFont(size=11))':
        '        self.option_caption = ctk.CTkLabel(self.controls_card, text="Qualidade e formato", text_color=MUTED, font=ctk.CTkFont(size=11))',
    '        self.option_menu = ctk.CTkOptionMenu(controls, variable=self.option_var, values=["Melhor qualidade"], height=38, corner_radius=13, fg_color=SURFACE_VARIANT, button_color=SECONDARY_CONTAINER, button_hover_color="#31477B", dropdown_fg_color=SURFACE_HIGH, text_color=TEXT, command=self._update_option_description)':
        '        self.option_menu = ctk.CTkOptionMenu(self.controls_card, variable=self.option_var, values=["Melhor qualidade"], height=38, corner_radius=13, fg_color=SURFACE_VARIANT, button_color=SECONDARY_CONTAINER, button_hover_color="#31477B", dropdown_fg_color=SURFACE_HIGH, text_color=TEXT, command=self._update_option_description)',
    '        self.option_description = ctk.CTkLabel(controls, text="", text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w", justify="left")':
        '        self.option_description = ctk.CTkLabel(self.controls_card, text="", text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w", justify="left")',
    '        folder = ctk.CTkFrame(self.details, fg_color=SURFACE, corner_radius=18)':
        '        self.folder_card = ctk.CTkFrame(self.details, fg_color=SURFACE, corner_radius=18)',
    '        folder.grid(row=2, column=0, sticky="ew", pady=(0, 8))':
        '        self.folder_card.grid(row=2, column=0, sticky="ew", pady=(0, 8))',
    '        folder.grid_columnconfigure(1, weight=1)':
        '        self.folder_card.grid_columnconfigure(1, weight=1)',
    '        ctk.CTkLabel(folder, text="▰", text_color=PRIMARY, font=ctk.CTkFont(size=17, weight="bold")).grid(row=0, column=0, rowspan=2, padx=(14, 9), pady=9)':
        '        ctk.CTkLabel(self.folder_card, text="▰", text_color=PRIMARY, font=ctk.CTkFont(size=17, weight="bold")).grid(row=0, column=0, rowspan=2, padx=(14, 9), pady=9)',
    '        ctk.CTkLabel(folder, text="Pasta de download", text_color=TEXT, font=ctk.CTkFont(size=12, weight="bold"), anchor="w").grid(row=0, column=1, sticky="sw", pady=(8, 0))':
        '        ctk.CTkLabel(self.folder_card, text="Pasta de download", text_color=TEXT, font=ctk.CTkFont(size=12, weight="bold"), anchor="w").grid(row=0, column=1, sticky="sw", pady=(8, 0))',
    '        self.folder_label = ctk.CTkLabel(folder, text="Downloads/GetMuvi", text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w")':
        '        self.folder_label = ctk.CTkLabel(self.folder_card, text="Downloads/GetMuvi", text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w")',
    '        ctk.CTkButton(folder, text="Alterar", width=88, height=32, corner_radius=12, fg_color="transparent", border_width=1, border_color="#3A4665", hover_color=SURFACE_VARIANT, command=self.choose_folder).grid(row=0, column=2, rowspan=2, padx=12)':
        '        ctk.CTkButton(self.folder_card, text="Alterar", width=88, height=32, corner_radius=12, fg_color="transparent", border_width=1, border_color="#3A4665", hover_color=SURFACE_VARIANT, command=self.choose_folder).grid(row=0, column=2, rowspan=2, padx=12)',
}

missing = []
for old, new in replacements.items():
    if old not in text:
        missing.append(old)
    else:
        text = text.replace(old, new, 1)

if missing:
    raise SystemExit("Trechos esperados não encontrados no patch responsivo 0.1.4:\n\n" + "\n---\n".join(missing))

old_responsive = '''    def _responsive_layout(self, event=None) -> None:\n        if event is not None and event.widget is not self:\n            return\n        width = max(self.winfo_width(), 900)\n        wrap = max(520, min(width - 120, 980))\n        self.media_title.configure(wraplength=wrap)\n        self.error_label.configure(wraplength=wrap)\n'''

new_responsive = '''    def _responsive_layout(self, event=None) -> None:\n        if event is not None and event.widget is not self:\n            return\n        width = max(self.winfo_width(), 780)\n        wrap = max(420, min(width - 120, 980))\n        self.media_title.configure(wraplength=wrap)\n        self.error_label.configure(wraplength=wrap)\n        if self.media_info is not None:\n            self._layout_analyzed(width)\n\n    def _layout_analyzed(self, width: int | None = None) -> None:\n        # Desktop: resultados começam imediatamente abaixo de Analisar.\n        # Em larguras normais, controles ficam à esquerda e pasta/download\n        # à direita. Assim tudo permanece visível sem scroll e sem maximizar.\n        width = width or max(self.winfo_width(), 780)\n        two_columns = width >= 820\n\n        self.content.grid_rowconfigure(4, weight=0)\n        self.message_frame.grid_remove()\n        self.initial_frame.grid_remove()\n        self.details.grid(row=3, column=0, sticky=\"ew\", pady=(0, 0))\n        self.details.grid_columnconfigure(0, weight=1)\n        self.details.grid_columnconfigure(1, weight=1 if two_columns else 0)\n        self.details.grid_rowconfigure(3, weight=0)\n\n        if two_columns:\n            self.media_card.grid(row=0, column=0, columnspan=2, sticky=\"ew\", pady=(0, 6))\n            self.controls_card.grid(row=1, column=0, rowspan=2, sticky=\"nsew\", padx=(0, 4), pady=(0, 0))\n            self.folder_card.grid(row=1, column=1, sticky=\"ew\", padx=(4, 0), pady=(0, 6))\n            self.action_card.grid(row=2, column=1, sticky=\"ew\", padx=(4, 0), pady=(0, 0))\n        else:\n            self.media_card.grid(row=0, column=0, columnspan=1, sticky=\"ew\", pady=(0, 6))\n            self.controls_card.grid(row=1, column=0, rowspan=1, sticky=\"ew\", padx=0, pady=(0, 6))\n            self.folder_card.grid(row=2, column=0, sticky=\"ew\", padx=0, pady=(0, 6))\n            self.action_card.grid(row=3, column=0, sticky=\"ew\", padx=0, pady=(0, 0))\n'''

if old_responsive not in text:
    raise SystemExit("Método _responsive_layout esperado não encontrado após o patch 0.1.3.")
text = text.replace(old_responsive, new_responsive, 1)

# Depois da análise, aplica imediatamente o layout responsivo, sem esperar resize.
needle = '        self.details.grid(sticky="ew")\n        self.status_var.set("Pronto para baixar")'
replacement = '        self.details.grid(sticky="ew")\n        self._layout_analyzed()\n        self.status_var.set("Pronto para baixar")'
if needle not in text:
    raise SystemExit("Ponto de ativação do layout analisado não encontrado.")
text = text.replace(needle, replacement, 1)

# Se voltar ao estado inicial, recoloca a mensagem na posição normal e limpa a área de resultados.
needle2 = '        self.content.grid_rowconfigure(4, weight=1)\n        self.initial_frame.grid(sticky="nsew")'
replacement2 = '        self.message_frame.grid(row=3, column=0, sticky="ew", pady=(0, 8))\n        self.content.grid_rowconfigure(4, weight=1)\n        self.initial_frame.grid(row=4, column=0, sticky="nsew")'
if needle2 in text:
    text = text.replace(needle2, replacement2, 1)

# Mensagens continuam visíveis mesmo quando a área de resultados usa a linha 3.
needle3 = '        self.success_label.configure(text="Download concluído com sucesso." if final_file is not None else "Download concluído. Abra a pasta para ver o arquivo.")\n        self.success_card.grid()'
replacement3 = '        self.success_label.configure(text="Download concluído com sucesso." if final_file is not None else "Download concluído. Abra a pasta para ver o arquivo.")\n        self.message_frame.grid(row=3, column=0, sticky="ew", pady=(0, 8))\n        self.success_card.grid()'
if needle3 not in text:
    raise SystemExit("Trecho de sucesso esperado não encontrado.")
text = text.replace(needle3, replacement3, 1)

needle4 = '    def _show_error(self, message: str) -> None:\n        self.error_label.configure(text=message)\n        self.error_card.grid()'
replacement4 = '    def _show_error(self, message: str) -> None:\n        self.error_label.configure(text=message)\n        if self.media_info is None:\n            self.message_frame.grid(row=3, column=0, sticky="ew", pady=(0, 8))\n        else:\n            self.content.grid_rowconfigure(4, weight=0)\n            self.message_frame.grid(row=4, column=0, sticky="ew", pady=(6, 0))\n        self.error_card.grid()'
if needle4 not in text:
    raise SystemExit("Trecho de erro esperado não encontrado.")
text = text.replace(needle4, replacement4, 1)

# Janela padrão um pouco mais compacta; segue redimensionável.
text = text.replace('        self.geometry("1040x720")\n        self.minsize(860, 620)',
                    '        self.geometry("1040x690")\n        self.minsize(780, 590)', 1)

path.write_text(text, encoding="utf-8")
print("GetMuvi Windows 0.1.4: layout responsivo de duas colunas aplicado.")
