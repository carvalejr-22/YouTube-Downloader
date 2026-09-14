from __future__ import annotations

import os
import re
import sys
import threading
import traceback
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import customtkinter as ctk
import tkinter as tk
from tkinter import filedialog, messagebox
from PIL import Image, ImageTk, ImageDraw
import yt_dlp

APP_NAME = "GetMuvi"
APP_VERSION = "0.1.2"

BG = "#070B18"
SURFACE = "#0B1020"
SURFACE_VARIANT = "#151B2E"
SURFACE_HIGH = "#111829"
TEXT = "#F5F7FF"
MUTED = "#B9C1D8"
PRIMARY = "#25D9D0"
PRIMARY_HOVER = "#1CB7B0"
PRIMARY_CONTAINER = "#103A45"
SECONDARY = "#7A9FFF"
SECONDARY_CONTAINER = "#243663"
PURPLE = "#765BFF"
ERROR_CONTAINER = "#4A2230"


class CancelledDownload(Exception):
    pass


@dataclass(frozen=True)
class DownloadOption:
    key: str
    group: str
    label: str
    description: str
    height: int | None = None
    container: str | None = None
    audio_format: str | None = None
    source_ext: str | None = None
    estimated_bytes: int | None = None


def resource_path(relative: str) -> str:
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return str(root / relative)


def bundled_ffmpeg_dir() -> str | None:
    candidate = Path(resource_path("bin"))
    if (candidate / "ffmpeg.exe").exists():
        return str(candidate)
    return None


def default_download_folder() -> Path:
    downloads = Path.home() / "Downloads"
    if not downloads.exists():
        downloads = Path.home()
    folder = downloads / "GetMuvi"
    folder.mkdir(parents=True, exist_ok=True)
    return folder


def clean_status(text: str) -> str:
    text = re.sub(r"\x1b\[[0-9;]*m", "", text or "")
    text = re.sub(r"\s+", " ", text).strip()
    return text[:150]


def format_duration(seconds: int | float | None) -> str:
    if not seconds:
        return ""
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def format_bytes(value: int | None) -> str:
    if not value:
        return ""
    mb = value / (1024 * 1024)
    return f"{mb / 1024:.1f} GB" if mb >= 1024 else f"{mb:.0f} MB"


class BrandBanner(tk.Canvas):
    """Banner responsivo com a mesma paleta e arte da versão Android."""

    COLORS = ((25, 231, 222), (9, 143, 255), (101, 76, 255), (231, 82, 255))

    def __init__(self, master, **kwargs):
        super().__init__(master, height=96, highlightthickness=0, bd=0, bg=BG, **kwargs)
        self._bg_photo = None
        self._logo_photo = None
        self._last_width = 0
        logo_path = resource_path("assets/getmuvi.png")
        if os.path.exists(logo_path):
            logo = Image.open(logo_path).convert("RGBA")
            logo.thumbnail((64, 64), Image.Resampling.LANCZOS)
            self._logo_photo = ImageTk.PhotoImage(logo)
        self.bind("<Configure>", self._on_resize)

    @staticmethod
    def _mix(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

    def _gradient(self, width: int, height: int) -> Image.Image:
        width = max(width, 20)
        image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        segments = len(self.COLORS) - 1
        for x in range(width):
            p = x / max(width - 1, 1)
            scaled = p * segments
            idx = min(int(scaled), segments - 1)
            t = scaled - idx
            rgb = self._mix(self.COLORS[idx], self.COLORS[idx + 1], t)
            draw.line((x, 0, x, height), fill=(*rgb, 255))
        mask = Image.new("L", (width, height), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=26, fill=255)
        image.putalpha(mask)
        return image

    def _on_resize(self, event):
        width = max(event.width, 20)
        if abs(width - self._last_width) < 4:
            return
        self._last_width = width
        image = self._gradient(width, 96)
        self._bg_photo = ImageTk.PhotoImage(image)
        self.delete("all")
        self.create_image(0, 0, image=self._bg_photo, anchor="nw")
        if self._logo_photo:
            self.create_image(52, 48, image=self._logo_photo, anchor="center")
        self.create_text(98, 36, text="Baixe do seu jeito", anchor="w", fill="white", font=("Segoe UI", 17, "bold"))
        self.create_text(98, 62, text="Escolha vídeo, áudio, qualidade e formato conforme a mídia oferecer.", anchor="w", fill="#F5F7FF", font=("Segoe UI", 10))


class GetMuviApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("blue")

        self.title(f"{APP_NAME} {APP_VERSION}")
        self.geometry("1040x760")
        self.minsize(900, 700)
        self.configure(fg_color=BG)

        icon = resource_path("assets/getmuvi.ico")
        if os.path.exists(icon):
            try:
                self.iconbitmap(icon)
            except Exception:
                pass

        self.url_var = tk.StringVar()
        self.folder_var = tk.StringVar(value=str(default_download_folder()))
        self.status_var = tk.StringVar(value="Cole um link para começar")
        self.progress_var = tk.DoubleVar(value=0.0)
        self.option_var = tk.StringVar(value="Melhor qualidade")

        self.media_info: dict[str, Any] | None = None
        self.options: list[DownloadOption] = []
        self.selected_group = "Completo"
        self.cancel_event = threading.Event()
        self.busy = False
        self.last_downloaded_file: Path | None = None
        self._option_lookup: dict[str, DownloadOption] = {}

        self._build_ui()
        self._show_initial_state()
        self.bind("<Configure>", self._responsive_layout)

    def _build_ui(self) -> None:
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        header = ctk.CTkFrame(self, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", padx=26, pady=(14, 5))
        header.grid_columnconfigure(2, weight=1)

        ctk.CTkLabel(header, text="Get", font=ctk.CTkFont(family="Segoe UI", size=28, weight="bold"), text_color=TEXT).grid(row=0, column=0, sticky="w")
        ctk.CTkLabel(header, text="Muvi", font=ctk.CTkFont(family="Segoe UI", size=28, weight="bold"), text_color=PRIMARY).grid(row=0, column=1, sticky="w")
        ctk.CTkLabel(header, text="Baixador de músicas e vídeos", font=ctk.CTkFont(family="Segoe UI", size=11), text_color=MUTED).grid(row=1, column=0, columnspan=3, sticky="w", pady=(0, 1))

        self.content = ctk.CTkFrame(self, fg_color="transparent")
        self.content.grid(row=1, column=0, sticky="nsew", padx=26, pady=(2, 18))
        self.content.grid_columnconfigure(0, weight=1)
        self.content.grid_rowconfigure(4, weight=1)

        self.brand = BrandBanner(self.content)
        self.brand.grid(row=0, column=0, sticky="ew", pady=(0, 10))

        link_card = ctk.CTkFrame(self.content, fg_color=SURFACE, corner_radius=20)
        link_card.grid(row=1, column=0, sticky="ew", pady=(0, 8))
        link_card.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(link_card, text="Cole o link da mídia ou playlist", text_color=MUTED, font=ctk.CTkFont(size=11)).grid(row=0, column=0, columnspan=2, sticky="w", padx=16, pady=(11, 4))

        self.url_entry = ctk.CTkEntry(link_card, textvariable=self.url_var, height=42, corner_radius=16, fg_color=SURFACE_VARIANT, border_color="#2B3450", text_color=TEXT, placeholder_text="https://...")
        self.url_entry.grid(row=1, column=0, sticky="ew", padx=(16, 8), pady=(0, 12))
        self.url_entry.bind("<KeyRelease>", lambda _e: self._on_url_changed())
        ctk.CTkButton(link_card, text="Colar", width=82, height=42, corner_radius=16, fg_color=SECONDARY_CONTAINER, hover_color="#31477B", text_color=TEXT, command=self._paste).grid(row=1, column=1, padx=(0, 16), pady=(0, 12))

        self.analyze_button = ctk.CTkButton(self.content, text="Analisar link", height=43, corner_radius=16, fg_color=PRIMARY, hover_color=PRIMARY_HOVER, text_color="#001F1E", font=ctk.CTkFont(size=14, weight="bold"), command=self.analyze)
        self.analyze_button.grid(row=2, column=0, sticky="ew", pady=(0, 8))

        self.message_frame = ctk.CTkFrame(self.content, fg_color="transparent")
        self.message_frame.grid(row=3, column=0, sticky="ew", pady=(0, 8))
        self.message_frame.grid_columnconfigure(0, weight=1)

        self.success_card = ctk.CTkFrame(self.message_frame, fg_color=SECONDARY_CONTAINER, corner_radius=16)
        self.success_card.grid(row=0, column=0, sticky="ew")
        self.success_card.grid_columnconfigure(0, weight=1)
        self.success_label = ctk.CTkLabel(self.success_card, text="Download concluído com sucesso.", text_color="#E7ECFF", font=ctk.CTkFont(size=12, weight="bold"), anchor="w")
        self.success_label.grid(row=0, column=0, sticky="ew", padx=14, pady=(10, 6))
        ctk.CTkButton(self.success_card, text="Abrir pasta", height=36, corner_radius=12, fg_color="transparent", border_width=1, border_color=SECONDARY, hover_color="#2D4275", text_color=TEXT, command=self.open_download_location).grid(row=1, column=0, sticky="ew", padx=14, pady=(0, 10))
        self.success_card.grid_remove()

        self.error_card = ctk.CTkFrame(self.message_frame, fg_color=ERROR_CONTAINER, corner_radius=16)
        self.error_card.grid(row=1, column=0, sticky="ew")
        self.error_label = ctk.CTkLabel(self.error_card, text="", text_color="#FFDCE3", wraplength=900, justify="left", anchor="w")
        self.error_label.grid(row=0, column=0, sticky="ew", padx=14, pady=12)
        self.error_card.grid_remove()

        self.initial_frame = ctk.CTkFrame(self.content, fg_color="transparent")
        self.initial_frame.grid(row=4, column=0, sticky="nsew")
        ctk.CTkLabel(self.initial_frame, text="⇩", text_color=PRIMARY, font=ctk.CTkFont(size=30, weight="bold")).pack(pady=(24, 4))
        ctk.CTkLabel(self.initial_frame, text="Cole um link para começar", text_color=TEXT, font=ctk.CTkFont(size=14, weight="bold")).pack()
        ctk.CTkLabel(self.initial_frame, text="O GetMuvi analisa a mídia e mostra somente as opções encontradas.", text_color=MUTED, font=ctk.CTkFont(size=11)).pack(pady=(4, 0))

        self.details = ctk.CTkFrame(self.content, fg_color="transparent")
        self.details.grid(row=4, column=0, sticky="nsew")
        self.details.grid_columnconfigure(0, weight=1)
        self.details.grid_rowconfigure(3, weight=1)
        self.details.grid_remove()

        self.media_card = ctk.CTkFrame(self.details, fg_color=SURFACE_HIGH, corner_radius=18)
        self.media_card.grid(row=0, column=0, sticky="ew", pady=(0, 8))
        self.media_card.grid_columnconfigure(0, weight=1)
        self.media_badge = ctk.CTkLabel(self.media_card, text="Mídia encontrada", text_color=PRIMARY, font=ctk.CTkFont(size=11, weight="bold"), anchor="w")
        self.media_badge.grid(row=0, column=0, sticky="ew", padx=15, pady=(11, 2))
        self.media_title = ctk.CTkLabel(self.media_card, text="", text_color=TEXT, font=ctk.CTkFont(size=16, weight="bold"), anchor="w", justify="left", wraplength=930)
        self.media_title.grid(row=1, column=0, sticky="ew", padx=15)
        self.media_subtitle = ctk.CTkLabel(self.media_card, text="", text_color=MUTED, font=ctk.CTkFont(size=11), anchor="w")
        self.media_subtitle.grid(row=2, column=0, sticky="ew", padx=15, pady=(2, 11))

        controls = ctk.CTkFrame(self.details, fg_color=SURFACE, corner_radius=18)
        controls.grid(row=1, column=0, sticky="ew", pady=(0, 8))
        controls.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(controls, text="O que você quer baixar?", text_color=TEXT, font=ctk.CTkFont(size=13, weight="bold")).grid(row=0, column=0, sticky="w", padx=15, pady=(11, 6))

        self.kind_row = ctk.CTkFrame(controls, fg_color="transparent")
        self.kind_row.grid(row=1, column=0, sticky="ew", padx=15)
        for i in range(3):
            self.kind_row.grid_columnconfigure(i, weight=1)
        self.kind_buttons: dict[str, ctk.CTkButton] = {}
        for idx, group in enumerate(("Completo", "Vídeo", "Áudio")):
            btn = ctk.CTkButton(self.kind_row, text=group, height=34, corner_radius=14, fg_color=SURFACE_VARIANT, hover_color="#24304B", border_width=1, border_color="#2B3450", text_color=TEXT, command=lambda g=group: self._set_group(g))
            btn.grid(row=0, column=idx, sticky="ew", padx=(0 if idx == 0 else 4, 0 if idx == 2 else 4))
            self.kind_buttons[group] = btn

        self.option_caption = ctk.CTkLabel(controls, text="Qualidade e formato", text_color=MUTED, font=ctk.CTkFont(size=11))
        self.option_caption.grid(row=2, column=0, sticky="w", padx=15, pady=(8, 3))
        self.option_menu = ctk.CTkOptionMenu(controls, variable=self.option_var, values=["Melhor qualidade"], height=38, corner_radius=13, fg_color=SURFACE_VARIANT, button_color=SECONDARY_CONTAINER, button_hover_color="#31477B", dropdown_fg_color=SURFACE_HIGH, text_color=TEXT, command=self._update_option_description)
        self.option_menu.grid(row=3, column=0, sticky="ew", padx=15)
        self.option_description = ctk.CTkLabel(controls, text="", text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w", justify="left")
        self.option_description.grid(row=4, column=0, sticky="ew", padx=15, pady=(4, 10))

        folder = ctk.CTkFrame(self.details, fg_color=SURFACE, corner_radius=18)
        folder.grid(row=2, column=0, sticky="ew", pady=(0, 8))
        folder.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(folder, text="▰", text_color=PRIMARY, font=ctk.CTkFont(size=17, weight="bold")).grid(row=0, column=0, rowspan=2, padx=(14, 9), pady=9)
        ctk.CTkLabel(folder, text="Pasta de download", text_color=TEXT, font=ctk.CTkFont(size=12, weight="bold"), anchor="w").grid(row=0, column=1, sticky="sw", pady=(8, 0))
        self.folder_label = ctk.CTkLabel(folder, text="Downloads/GetMuvi", text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w")
        self.folder_label.grid(row=1, column=1, sticky="nw", pady=(0, 8))
        ctk.CTkButton(folder, text="Alterar", width=88, height=32, corner_radius=12, fg_color="transparent", border_width=1, border_color="#3A4665", hover_color=SURFACE_VARIANT, command=self.choose_folder).grid(row=0, column=2, rowspan=2, padx=12)

        self.action_card = ctk.CTkFrame(self.details, fg_color=PRIMARY_CONTAINER, corner_radius=18)
        self.action_card.grid(row=3, column=0, sticky="nsew")
        self.action_card.grid_columnconfigure(0, weight=1)

        self.progress_header = ctk.CTkFrame(self.action_card, fg_color="transparent")
        self.progress_header.grid(row=0, column=0, sticky="ew", padx=14, pady=(10, 0))
        self.progress_header.grid_columnconfigure(0, weight=1)
        self.progress_title = ctk.CTkLabel(self.progress_header, text="Baixando", text_color=TEXT, font=ctk.CTkFont(size=12, weight="bold"))
        self.progress_title.grid(row=0, column=0, sticky="w")
        self.progress_percent = ctk.CTkLabel(self.progress_header, text="0%", text_color=TEXT, font=ctk.CTkFont(size=12, weight="bold"))
        self.progress_percent.grid(row=0, column=1, sticky="e")

        self.progress = ctk.CTkProgressBar(self.action_card, variable=self.progress_var, height=10, corner_radius=7, progress_color=PRIMARY, fg_color="#0C2830")
        self.progress.grid(row=1, column=0, sticky="ew", padx=14, pady=(5, 5))
        self.status_label = ctk.CTkLabel(self.action_card, textvariable=self.status_var, text_color=MUTED, font=ctk.CTkFont(size=10), anchor="w")
        self.status_label.grid(row=2, column=0, sticky="ew", padx=14, pady=(0, 7))

        self.action_buttons = ctk.CTkFrame(self.action_card, fg_color="transparent")
        self.action_buttons.grid(row=3, column=0, sticky="ew", padx=14, pady=(0, 10))
        self.action_buttons.grid_columnconfigure(0, weight=1)
        self.download_button = ctk.CTkButton(self.action_buttons, text="Baixar agora", height=38, corner_radius=14, fg_color=PRIMARY, hover_color=PRIMARY_HOVER, text_color="#001F1E", font=ctk.CTkFont(size=13, weight="bold"), command=self.download)
        self.download_button.grid(row=0, column=0, sticky="ew")
        self.cancel_button = ctk.CTkButton(self.action_buttons, text="Cancelar", width=100, height=38, corner_radius=14, fg_color="transparent", border_width=1, border_color="#6A3A48", hover_color=ERROR_CONTAINER, text_color="#FFDCE3", command=self.cancel_download)
        self.cancel_button.grid(row=0, column=1, padx=(8, 0))
        self.cancel_button.grid_remove()

    def _responsive_layout(self, event=None) -> None:
        if event is not None and event.widget is not self:
            return
        width = max(self.winfo_width(), 900)
        wrap = max(520, min(width - 120, 980))
        self.media_title.configure(wraplength=wrap)
        self.error_label.configure(wraplength=wrap)

    def _show_initial_state(self) -> None:
        self.details.grid_remove()
        self.success_card.grid_remove()
        self.error_card.grid_remove()
        self.initial_frame.grid()
        self.status_var.set("Cole um link para começar")
        self.progress_var.set(0.0)
        self.progress_percent.configure(text="0%")
        self._set_group("Completo", refresh=False)

    def _on_url_changed(self) -> None:
        self.success_card.grid_remove()
        self.error_card.grid_remove()
        if not self.media_info and not self.busy:
            self.initial_frame.grid()

    def _paste(self) -> None:
        try:
            self.url_var.set(self.clipboard_get().strip())
            self._on_url_changed()
        except Exception:
            pass

    def choose_folder(self) -> None:
        chosen = filedialog.askdirectory(initialdir=self.folder_var.get() or str(Path.home()))
        if chosen:
            self.folder_var.set(chosen)
            self._refresh_folder_label()

    def _refresh_folder_label(self) -> None:
        folder = Path(self.folder_var.get().strip() or default_download_folder())
        try:
            if folder.resolve() == default_download_folder().resolve():
                self.folder_label.configure(text="Downloads/GetMuvi")
            else:
                self.folder_label.configure(text=str(folder))
        except Exception:
            self.folder_label.configure(text=str(folder))

    def _set_busy(self, value: bool, status: str | None = None) -> None:
        self.busy = value
        self.analyze_button.configure(state="disabled" if value else "normal")
        self.download_button.configure(state="disabled" if value else "normal")
        if value and self.media_info:
            self.cancel_button.grid()
        else:
            self.cancel_button.grid_remove()
        if status is not None:
            self.status_var.set(status)

    def _ui(self, fn, *args) -> None:
        self.after(0, lambda: fn(*args))

    def analyze(self) -> None:
        url = self.url_var.get().strip()
        if not url.startswith(("http://", "https://")):
            self._show_error("Cole um link válido.")
            return
        if self.busy:
            return
        self.success_card.grid_remove()
        self.error_card.grid_remove()
        self.initial_frame.grid_remove()
        self.details.grid_remove()
        self.analyze_button.configure(text="Analisando formatos…")
        self._set_busy(True, "Analisando mídia e formatos…")
        threading.Thread(target=self._analyze_worker, args=(url,), daemon=True).start()

    def _analyze_worker(self, url: str) -> None:
        try:
            opts = {"quiet": True, "no_warnings": True, "skip_download": True, "extract_flat": "in_playlist", "noplaylist": False}
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            if not info:
                raise RuntimeError("Nenhuma mídia foi encontrada.")
            options = self._build_options(info)
            self.media_info = info
            self.options = options
            self._ui(self._apply_media_info, info, options)
        except Exception as exc:
            self.media_info = None
            self.options = []
            self._ui(self._show_error, self._friendly_error(exc))
        finally:
            self._ui(self._analysis_finished)

    def _analysis_finished(self) -> None:
        self.analyze_button.configure(text="Analisar link")
        self._set_busy(False, None)

    def _build_options(self, info: dict[str, Any]) -> list[DownloadOption]:
        entries = info.get("entries")
        if entries:
            return [
                DownloadOption("best-av", "Completo", "Melhor qualidade", "Cada item usa a melhor qualidade disponível"),
                DownloadOption("playlist-mp4", "Completo", "MP4 automático", "Prioriza MP4 em cada item da lista", container="mp4"),
                DownloadOption("playlist-video", "Vídeo", "Melhor vídeo", "Somente a faixa de vídeo de cada item"),
                DownloadOption("audio-mp3", "Áudio", "MP3", "Converte o melhor áudio de cada item para MP3", audio_format="mp3"),
                DownloadOption("audio-m4a", "Áudio", "M4A", "Converte o melhor áudio de cada item para M4A", audio_format="m4a"),
                DownloadOption("audio-opus", "Áudio", "Opus", "Converte o melhor áudio de cada item para Opus", audio_format="opus"),
                DownloadOption("audio-original", "Áudio", "Original", "Mantém o melhor formato de áudio disponível em cada item"),
            ]

        formats = info.get("formats") or []
        heights: dict[int, list[tuple[str, int | None]]] = {}
        audio_exts: set[str] = set()
        has_audio = False
        for fmt in formats:
            vcodec = fmt.get("vcodec") or "none"
            acodec = fmt.get("acodec") or "none"
            ext = (fmt.get("ext") or "").lower()
            height = fmt.get("height")
            size = fmt.get("filesize") or fmt.get("filesize_approx")
            if acodec != "none":
                has_audio = True
            if vcodec != "none" and isinstance(height, int) and height > 0:
                heights.setdefault(height, []).append((ext, int(size) if size else None))
            if acodec != "none" and vcodec == "none" and ext:
                audio_exts.add(ext)

        result = [DownloadOption("best-av", "Completo", "Melhor qualidade", "Escolhe automaticamente a melhor combinação de vídeo + áudio")]
        for h in sorted(heights, reverse=True):
            values = heights[h]
            exts = list(dict.fromkeys([e for e in ("mp4", "webm") if any(x[0] == e for x in values)] + sorted({x[0] for x in values if x[0] and x[0] not in {"mp4", "webm"}})))
            for ext in exts:
                sizes = [size for e, size in values if e == ext and size]
                size = max(sizes) if sizes else None
                suffix = f" · aprox. {format_bytes(size)}" if size else ""
                result.append(DownloadOption(f"av-{h}-{ext}", "Completo", f"{h}p · {ext.upper()}", f"Vídeo + áudio{suffix}", height=h, container=ext, estimated_bytes=size))
        for h in sorted(heights, reverse=True):
            values = heights[h]
            exts = list(dict.fromkeys([e for e in ("mp4", "webm") if any(x[0] == e for x in values)] + sorted({x[0] for x in values if x[0] and x[0] not in {"mp4", "webm"}})))
            for ext in exts:
                sizes = [size for e, size in values if e == ext and size]
                size = max(sizes) if sizes else None
                suffix = f" · aprox. {format_bytes(size)}" if size else ""
                result.append(DownloadOption(f"video-{h}-{ext}", "Vídeo", f"{h}p · {ext.upper()}", f"Somente vídeo, sem faixa de áudio{suffix}", height=h, container=ext, estimated_bytes=size))
        if has_audio:
            result.extend([
                DownloadOption("audio-mp3", "Áudio", "MP3", "Alta compatibilidade · melhor áudio disponível", audio_format="mp3"),
                DownloadOption("audio-m4a", "Áudio", "M4A", "Boa qualidade com arquivo compacto", audio_format="m4a"),
                DownloadOption("audio-opus", "Áudio", "Opus", "Formato moderno e eficiente", audio_format="opus"),
                DownloadOption("audio-original", "Áudio", "Original", "Mantém o melhor formato de áudio fornecido pela plataforma"),
            ])
            if "webm" in audio_exts:
                result.append(DownloadOption("audio-webm", "Áudio", "WebM", "Faixa WebM original disponível nesta mídia", source_ext="webm"))
        return list({item.key: item for item in result}.values())

    def _apply_media_info(self, info: dict[str, Any], options: list[DownloadOption]) -> None:
        entries = info.get("entries") or []
        title = info.get("title") or "Mídia encontrada"
        uploader = info.get("uploader") or info.get("channel") or ""
        duration = format_duration(info.get("duration"))
        complete_count = sum(1 for o in options if o.group == "Completo")
        self.media_badge.configure(text="Lista encontrada" if entries else "Mídia encontrada")
        self.media_title.configure(text=title)
        parts = [p for p in (uploader, duration) if p]
        parts.append(f"{len(entries)} itens" if entries else f"{complete_count} opções de vídeo")
        self.media_subtitle.configure(text=" • ".join(parts))
        self.initial_frame.grid_remove()
        self.success_card.grid_remove()
        self.error_card.grid_remove()
        self.details.grid()
        self.status_var.set("Pronto para baixar")
        self._refresh_folder_label()
        preferred = next((g for g in ("Completo", "Vídeo", "Áudio") if any(o.group == g for o in options)), "Completo")
        self._set_group(preferred)
        self.download_button.configure(text=f"Baixar lista ({len(entries)})" if entries else "Baixar agora")
        self.progress_header.grid_remove()
        self.progress.grid_remove()
        self.status_label.grid_remove()
        self.cancel_button.grid_remove()

    def _set_group(self, group: str, refresh: bool = True) -> None:
        self.selected_group = group
        for name, btn in self.kind_buttons.items():
            selected = name == group
            btn.configure(fg_color=PURPLE if selected else SURFACE_VARIANT, border_color=PURPLE if selected else "#2B3450", text_color="white")
        self.option_caption.configure(text={"Completo": "Qualidade e formato", "Vídeo": "Qualidade do vídeo", "Áudio": "Formato do áudio"}[group])
        if refresh:
            self._refresh_option_menu()

    def _refresh_option_menu(self) -> None:
        filtered = [o for o in self.options if o.group == self.selected_group]
        self._option_lookup = {o.label: o for o in filtered}
        if not filtered:
            self.option_menu.configure(values=["Indisponível"], state="disabled")
            self.option_var.set("Indisponível")
            self.option_description.configure(text="Não há opções disponíveis nesse grupo para o link analisado.")
            self.download_button.configure(state="disabled")
            return
        values = [o.label for o in filtered]
        self.option_menu.configure(values=values, state="normal")
        self.option_var.set(values[0])
        self._update_option_description(values[0])
        self.download_button.configure(state="normal" if not self.busy else "disabled")

    def _selected_option(self) -> DownloadOption | None:
        return self._option_lookup.get(self.option_var.get())

    def _update_option_description(self, label: str) -> None:
        option = self._option_lookup.get(label)
        self.option_description.configure(text=option.description if option else "Opção indisponível.")

    def download(self) -> None:
        url = self.url_var.get().strip()
        option = self._selected_option()
        if not url.startswith(("http://", "https://")):
            self._show_error("Cole e analise um link válido primeiro.")
            return
        if option is None:
            self._show_error("Escolha uma qualidade ou formato disponível.")
            return
        folder = Path(self.folder_var.get().strip() or default_download_folder())
        try:
            folder.mkdir(parents=True, exist_ok=True)
        except Exception as exc:
            self._show_error(f"Não foi possível usar a pasta escolhida:\n{exc}")
            return
        if self.busy:
            return

        before = self._snapshot_folder(folder)
        self.cancel_event.clear()
        self.success_card.grid_remove()
        self.error_card.grid_remove()
        self.progress_var.set(0.0)
        self.progress_percent.configure(text="0%")
        self.progress_header.grid()
        self.progress.grid()
        self.status_label.grid()
        self.cancel_button.grid()
        self.download_button.configure(text="Baixando…")
        self._set_busy(True, "Preparando download…")
        threading.Thread(target=self._download_worker, args=(url, option, folder, before), daemon=True).start()

    @staticmethod
    def _snapshot_folder(folder: Path) -> dict[str, int]:
        snapshot = {}
        try:
            for p in folder.iterdir():
                if p.is_file():
                    snapshot[str(p.resolve())] = p.stat().st_mtime_ns
        except Exception:
            pass
        return snapshot

    def _detect_downloaded_file(self, folder: Path, before: dict[str, int]) -> Path | None:
        candidates: list[tuple[int, Path]] = []
        try:
            for p in folder.iterdir():
                if not p.is_file() or p.name.lower().endswith((".part", ".ytdl", ".tmp", ".temp")):
                    continue
                key = str(p.resolve())
                mtime = p.stat().st_mtime_ns
                if key not in before or before.get(key) != mtime:
                    candidates.append((mtime, p))
        except Exception:
            return None
        return max(candidates, key=lambda item: item[0])[1] if candidates else None

    def _download_worker(self, url: str, option: DownloadOption, folder: Path, before: dict[str, int]) -> None:
        try:
            with yt_dlp.YoutubeDL(self._ydl_options(option, folder)) as ydl:
                ydl.download([url])
            if self.cancel_event.is_set():
                raise CancelledDownload()
            self._ui(self._download_success, folder, self._detect_downloaded_file(folder, before))
        except CancelledDownload:
            self._ui(self._download_cancelled)
        except Exception as exc:
            traceback.print_exc()
            self._ui(self._show_error, self._friendly_error(exc))
        finally:
            self._ui(self._download_finished)

    def _ydl_options(self, option: DownloadOption, folder: Path) -> dict[str, Any]:
        is_playlist = bool((self.media_info or {}).get("entries"))
        outtmpl = str(folder / ("%(playlist_index)02d - %(title).46s.%(ext)s" if is_playlist else "%(title).60s.%(ext)s"))
        opts: dict[str, Any] = {"outtmpl": outtmpl, "noplaylist": False, "ignoreerrors": is_playlist, "continuedl": True, "noprogress": True, "quiet": True, "no_warnings": True, "progress_hooks": [self._progress_hook], "windowsfilenames": True, "overwrites": False, "retries": 3, "fragment_retries": 3}
        ffmpeg_dir = bundled_ffmpeg_dir()
        if ffmpeg_dir:
            opts["ffmpeg_location"] = ffmpeg_dir

        if option.key == "best-av":
            opts["format"] = "bestvideo*+bestaudio/best"
            opts["merge_output_format"] = "mkv"
        elif option.key == "playlist-mp4":
            opts["format"] = "best[ext=mp4]/bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
            opts["merge_output_format"] = "mp4"
        elif option.key == "playlist-video":
            opts["format"] = "bestvideo/bv"
        elif option.group == "Completo" and option.height:
            ext = option.container or "mp4"
            if ext == "mp4":
                opts["format"] = f"bv*[height={option.height}][ext=mp4]+ba[ext=m4a]/b[height={option.height}][ext=mp4]/bv*[height={option.height}]+ba/b[height={option.height}]"
                opts["merge_output_format"] = "mp4"
            elif ext == "webm":
                opts["format"] = f"bv*[height={option.height}][ext=webm]+ba[ext=webm]/b[height={option.height}][ext=webm]/bv*[height={option.height}]+ba/b[height={option.height}]"
                opts["merge_output_format"] = "webm"
            else:
                opts["format"] = f"bv*[height={option.height}]+ba/b[height={option.height}]"
                opts["merge_output_format"] = "mkv"
        elif option.group == "Vídeo" and option.height:
            ext = option.container or "mp4"
            opts["format"] = f"bv*[height={option.height}][ext={ext}]/bv*[height={option.height}]/bestvideo"
        elif option.audio_format:
            opts["format"] = "bestaudio/best"
            opts["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": option.audio_format, "preferredquality": "0"}]
        elif option.source_ext:
            opts["format"] = f"bestaudio[ext={option.source_ext}]/bestaudio/best"
        else:
            opts["format"] = "bestaudio/best"
        return opts

    def _progress_hook(self, data: dict[str, Any]) -> None:
        if self.cancel_event.is_set():
            raise CancelledDownload()
        status = data.get("status")
        if status == "downloading":
            downloaded = data.get("downloaded_bytes") or 0
            total = data.get("total_bytes") or data.get("total_bytes_estimate") or 0
            progress = max(0.0, min(downloaded / total if total else 0.0, 1.0))
            speed = clean_status(data.get("_speed_str") or "")
            eta = clean_status(data.get("_eta_str") or "")
            text = "Baixando…"
            if speed:
                text += f" · {speed}"
            if eta:
                text += f" · {eta} restantes"
            self._ui(self.progress_var.set, progress)
            self._ui(self.progress_percent.configure, text=f"{int(progress * 100)}%")
            self._ui(self.status_var.set, text)
        elif status == "finished":
            self._ui(self.progress_var.set, 1.0)
            self._ui(self.progress_percent.configure, text="100%")
            self._ui(self.status_var.set, "Processando arquivo…")

    def cancel_download(self) -> None:
        if self.busy:
            self.cancel_event.set()
            self.status_var.set("Cancelando…")

    def _download_success(self, folder: Path, final_file: Path | None) -> None:
        self.last_downloaded_file = final_file
        self.folder_var.set(str(folder))
        self.media_info = None
        self.options = []
        self._option_lookup.clear()
        self.url_var.set("")
        self.details.grid_remove()
        self.initial_frame.grid_remove()
        self.error_card.grid_remove()
        self.success_label.configure(text="Download concluído com sucesso." if final_file is not None else "Download concluído. Abra a pasta para ver o arquivo.")
        self.success_card.grid()
        self.progress_var.set(0.0)
        self.status_var.set("Download concluído com sucesso")

    def _download_cancelled(self) -> None:
        self.progress_var.set(0.0)
        self.progress_percent.configure(text="0%")
        self.status_var.set("Download cancelado")

    def _download_finished(self) -> None:
        self.download_button.configure(text="Baixar agora")
        self._set_busy(False, None)
        self.cancel_button.grid_remove()

    def open_download_location(self) -> None:
        target = self.last_downloaded_file
        folder = Path(self.folder_var.get().strip() or default_download_folder())
        try:
            if target is not None and target.exists():
                subprocess.Popen(["explorer.exe", "/select,", str(target)])
            else:
                folder.mkdir(parents=True, exist_ok=True)
                os.startfile(str(folder))
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Não foi possível abrir a pasta:\n{exc}")

    def _show_error(self, message: str) -> None:
        self.error_label.configure(text=message)
        self.error_card.grid()
        self.success_card.grid_remove()
        if self.media_info is None:
            self.details.grid_remove()
            self.initial_frame.grid()
        self.status_var.set("Não foi possível concluir a operação")

    @staticmethod
    def _friendly_error(exc: Exception) -> str:
        raw = str(exc)
        low = raw.lower()
        if "403" in low or "forbidden" in low:
            return "A plataforma recusou a rota de download (erro 403). Tente novamente em alguns instantes."
        if "login" in low or "cookies" in low or "private" in low:
            return "Esse conteúdo parece exigir login, cookies ou permissão privada. Use um link público e acessível."
        if "requested format is not available" in low:
            return "Esse formato não está disponível para este link. Escolha outra qualidade ou formato."
        if "filename too long" in low or "file name too long" in low:
            return "A plataforma gerou um nome de arquivo incompatível com o Windows."
        if isinstance(exc, CancelledDownload):
            return "Download cancelado."
        return raw or "Não foi possível concluir a operação."


if __name__ == "__main__":
    app = GetMuviApp()
    app.mainloop()
