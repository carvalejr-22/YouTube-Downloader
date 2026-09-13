from __future__ import annotations

import os
import re
import sys
import threading
import traceback
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import customtkinter as ctk
import tkinter as tk
from tkinter import filedialog, messagebox
import yt_dlp

APP_NAME = "GetMuvi"
APP_VERSION = "0.1.0"
BG = "#07152B"
CARD = "#0E2140"
CARD_2 = "#10284C"
TEXT = "#F6F9FF"
MUTED = "#9EB1CC"
CYAN = "#2DE7E3"
BLUE = "#168DFF"
PURPLE = "#8B5CFF"
SUCCESS = "#30D98A"
DANGER = "#FF6B7A"


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


class GetMuviApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("blue")

        self.title(f"{APP_NAME} {APP_VERSION}")
        self.geometry("980x760")
        self.minsize(860, 680)
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
        self.group_var = tk.StringVar(value="Completo")
        self.option_var = tk.StringVar(value="Melhor qualidade")

        self.media_info: dict[str, Any] | None = None
        self.options: list[DownloadOption] = []
        self.cancel_event = threading.Event()
        self.busy = False

        self._build_ui()

    def _build_ui(self) -> None:
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        header = ctk.CTkFrame(self, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", padx=28, pady=(20, 8))
        header.grid_columnconfigure(2, weight=1)

        ctk.CTkLabel(header, text="Get", font=ctk.CTkFont(size=29, weight="bold"), text_color=TEXT).grid(row=0, column=0, sticky="w")
        ctk.CTkLabel(header, text="Muvi", font=ctk.CTkFont(size=29, weight="bold"), text_color=CYAN).grid(row=0, column=1, sticky="w")
        ctk.CTkLabel(header, text="Baixador de músicas e vídeos", font=ctk.CTkFont(size=13), text_color=MUTED).grid(row=1, column=0, columnspan=3, sticky="w", pady=(0, 2))

        body = ctk.CTkScrollableFrame(self, fg_color="transparent", corner_radius=0)
        body.grid(row=1, column=0, sticky="nsew", padx=22, pady=(4, 18))
        body.grid_columnconfigure(0, weight=1)

        brand = ctk.CTkFrame(body, fg_color=CARD, corner_radius=24, border_width=1, border_color="#173D69")
        brand.grid(row=0, column=0, sticky="ew", padx=4, pady=(4, 14))
        brand.grid_columnconfigure(1, weight=1)

        icon_box = ctk.CTkFrame(brand, width=64, height=64, fg_color="#112E58", corner_radius=18)
        icon_box.grid(row=0, column=0, rowspan=2, padx=18, pady=18)
        icon_box.grid_propagate(False)
        ctk.CTkLabel(icon_box, text="⇩♫", font=ctk.CTkFont(size=25, weight="bold"), text_color=CYAN).place(relx=.5, rely=.5, anchor="center")
        ctk.CTkLabel(brand, text="Baixe do seu jeito", font=ctk.CTkFont(size=20, weight="bold"), text_color=TEXT).grid(row=0, column=1, sticky="sw", pady=(18, 2), padx=(0, 18))
        ctk.CTkLabel(brand, text="Vídeo + áudio, somente vídeo ou somente áudio, com qualidade e formato à sua escolha.", font=ctk.CTkFont(size=13), text_color=MUTED, wraplength=720, justify="left").grid(row=1, column=1, sticky="nw", pady=(0, 18), padx=(0, 18))

        link_card = ctk.CTkFrame(body, fg_color=CARD, corner_radius=22)
        link_card.grid(row=1, column=0, sticky="ew", padx=4, pady=(0, 12))
        link_card.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(link_card, text="Link da mídia ou playlist", text_color=TEXT, font=ctk.CTkFont(size=14, weight="bold")).grid(row=0, column=0, columnspan=2, sticky="w", padx=18, pady=(16, 8))
        self.url_entry = ctk.CTkEntry(link_card, textvariable=self.url_var, height=46, corner_radius=16, fg_color="#081B36", border_color="#254B76", text_color=TEXT, placeholder_text="https://...")
        self.url_entry.grid(row=1, column=0, sticky="ew", padx=(18, 8), pady=(0, 16))
        ctk.CTkButton(link_card, text="Colar", width=86, height=46, corner_radius=16, fg_color="#173D68", hover_color="#205488", command=self._paste).grid(row=1, column=1, padx=(0, 18), pady=(0, 16))

        self.analyze_button = ctk.CTkButton(body, text="Analisar link", height=48, corner_radius=16, fg_color=BLUE, hover_color="#0F76D8", font=ctk.CTkFont(size=15, weight="bold"), command=self.analyze)
        self.analyze_button.grid(row=2, column=0, sticky="ew", padx=4, pady=(0, 12))

        self.media_card = ctk.CTkFrame(body, fg_color=CARD_2, corner_radius=20)
        self.media_card.grid(row=3, column=0, sticky="ew", padx=4, pady=(0, 12))
        self.media_card.grid_columnconfigure(0, weight=1)
        self.media_title = ctk.CTkLabel(self.media_card, text="Nenhuma mídia analisada", text_color=TEXT, font=ctk.CTkFont(size=17, weight="bold"), anchor="w", justify="left", wraplength=820)
        self.media_title.grid(row=0, column=0, sticky="ew", padx=18, pady=(15, 2))
        self.media_subtitle = ctk.CTkLabel(self.media_card, text="As opções aparecerão aqui depois da análise.", text_color=MUTED, font=ctk.CTkFont(size=12), anchor="w")
        self.media_subtitle.grid(row=1, column=0, sticky="ew", padx=18, pady=(0, 15))

        options_card = ctk.CTkFrame(body, fg_color=CARD, corner_radius=22)
        options_card.grid(row=4, column=0, sticky="ew", padx=4, pady=(0, 12))
        options_card.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(options_card, text="O que você quer baixar?", text_color=TEXT, font=ctk.CTkFont(size=15, weight="bold")).grid(row=0, column=0, sticky="w", padx=18, pady=(16, 8))
        self.segment = ctk.CTkSegmentedButton(options_card, values=["Completo", "Vídeo", "Áudio"], variable=self.group_var, selected_color=PURPLE, selected_hover_color="#7447E4", unselected_color="#132B4D", unselected_hover_color="#1A3A64", command=lambda _: self._refresh_option_menu())
        self.segment.grid(row=1, column=0, sticky="ew", padx=18, pady=(0, 12))

        ctk.CTkLabel(options_card, text="Qualidade / formato", text_color=MUTED, font=ctk.CTkFont(size=12)).grid(row=2, column=0, sticky="w", padx=18, pady=(0, 4))
        self.option_menu = ctk.CTkOptionMenu(options_card, variable=self.option_var, values=["Melhor qualidade"], height=44, corner_radius=14, fg_color="#102C52", button_color="#17477C", button_hover_color="#1C5C9E", dropdown_fg_color="#0E2547", command=self._update_option_description)
        self.option_menu.grid(row=3, column=0, sticky="ew", padx=18, pady=(0, 5))
        self.option_description = ctk.CTkLabel(options_card, text="Escolha uma opção após analisar o link.", text_color=MUTED, font=ctk.CTkFont(size=12), anchor="w", justify="left", wraplength=820)
        self.option_description.grid(row=4, column=0, sticky="ew", padx=18, pady=(0, 16))

        folder_card = ctk.CTkFrame(body, fg_color=CARD, corner_radius=22)
        folder_card.grid(row=5, column=0, sticky="ew", padx=4, pady=(0, 12))
        folder_card.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(folder_card, text="Pasta de destino", text_color=TEXT, font=ctk.CTkFont(size=14, weight="bold")).grid(row=0, column=0, columnspan=2, sticky="w", padx=18, pady=(15, 6))
        self.folder_entry = ctk.CTkEntry(folder_card, textvariable=self.folder_var, height=42, corner_radius=14, fg_color="#081B36", border_color="#254B76", text_color=MUTED)
        self.folder_entry.grid(row=1, column=0, sticky="ew", padx=(18, 8), pady=(0, 15))
        ctk.CTkButton(folder_card, text="Escolher", width=100, height=42, corner_radius=14, fg_color="#173D68", hover_color="#205488", command=self.choose_folder).grid(row=1, column=1, padx=(0, 18), pady=(0, 15))

        action_card = ctk.CTkFrame(body, fg_color=CARD, corner_radius=22)
        action_card.grid(row=6, column=0, sticky="ew", padx=4, pady=(0, 14))
        action_card.grid_columnconfigure(0, weight=1)
        self.progress = ctk.CTkProgressBar(action_card, variable=self.progress_var, height=12, corner_radius=8, progress_color=CYAN, fg_color="#102A49")
        self.progress.grid(row=0, column=0, columnspan=2, sticky="ew", padx=18, pady=(18, 8))
        self.status_label = ctk.CTkLabel(action_card, textvariable=self.status_var, text_color=MUTED, font=ctk.CTkFont(size=12), anchor="w")
        self.status_label.grid(row=1, column=0, columnspan=2, sticky="ew", padx=18, pady=(0, 12))
        self.download_button = ctk.CTkButton(action_card, text="Baixar", height=48, corner_radius=16, fg_color=PURPLE, hover_color="#7447E4", font=ctk.CTkFont(size=15, weight="bold"), command=self.download)
        self.download_button.grid(row=2, column=0, sticky="ew", padx=(18, 7), pady=(0, 16))
        self.cancel_button = ctk.CTkButton(action_card, text="Cancelar", width=110, height=48, corner_radius=16, fg_color="#4B2637", hover_color="#6A324B", text_color="#FFDCE3", state="disabled", command=self.cancel_download)
        self.cancel_button.grid(row=2, column=1, padx=(0, 18), pady=(0, 16))

        self.success_card = ctk.CTkFrame(body, fg_color="#0C3A32", corner_radius=18, border_width=1, border_color="#186750")
        self.success_card.grid(row=7, column=0, sticky="ew", padx=4, pady=(0, 14))
        self.success_card.grid_columnconfigure(0, weight=1)
        self.success_label = ctk.CTkLabel(self.success_card, text="", text_color="#CFFFF0", font=ctk.CTkFont(size=13, weight="bold"), anchor="w")
        self.success_label.grid(row=0, column=0, sticky="ew", padx=16, pady=(13, 8))
        ctk.CTkButton(self.success_card, text="Abrir pasta", height=38, corner_radius=13, fg_color="#156953", hover_color="#198165", command=self.open_folder).grid(row=1, column=0, sticky="ew", padx=16, pady=(0, 13))
        self.success_card.grid_remove()

    def _paste(self) -> None:
        try:
            self.url_var.set(self.clipboard_get().strip())
            self.success_card.grid_remove()
        except Exception:
            pass

    def choose_folder(self) -> None:
        chosen = filedialog.askdirectory(initialdir=self.folder_var.get() or str(Path.home()))
        if chosen:
            self.folder_var.set(chosen)
            self.success_card.grid_remove()

    def _set_busy(self, value: bool, status: str | None = None) -> None:
        self.busy = value
        self.analyze_button.configure(state="disabled" if value else "normal")
        self.download_button.configure(state="disabled" if value else "normal")
        self.cancel_button.configure(state="normal" if value else "disabled")
        if status:
            self.status_var.set(status)

    def _ui(self, fn, *args) -> None:
        self.after(0, lambda: fn(*args))

    def analyze(self) -> None:
        url = self.url_var.get().strip()
        if not url.startswith(("http://", "https://")):
            messagebox.showwarning(APP_NAME, "Cole um link válido.")
            return
        if self.busy:
            return
        self.success_card.grid_remove()
        self.progress_var.set(0.0)
        self._set_busy(True, "Analisando mídia e formatos…")
        threading.Thread(target=self._analyze_worker, args=(url,), daemon=True).start()

    def _analyze_worker(self, url: str) -> None:
        try:
            opts = {
                "quiet": True,
                "no_warnings": True,
                "skip_download": True,
                "extract_flat": "in_playlist",
                "noplaylist": False,
            }
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            if not info:
                raise RuntimeError("Nenhuma mídia foi encontrada.")
            options = self._build_options(info)
            self.media_info = info
            self.options = options
            self._ui(self._apply_media_info, info, options)
        except Exception as exc:
            self._ui(self._show_error, self._friendly_error(exc))
        finally:
            self._ui(self._set_busy, False, None)

    def _build_options(self, info: dict[str, Any]) -> list[DownloadOption]:
        entries = info.get("entries")
        is_playlist = bool(entries)
        if is_playlist:
            return [
                DownloadOption("best-av", "Completo", "Melhor qualidade", "Cada item usa a melhor qualidade disponível."),
                DownloadOption("playlist-mp4", "Completo", "MP4 automático", "Prioriza MP4 quando disponível.", container="mp4"),
                DownloadOption("video-best", "Vídeo", "Melhor vídeo", "Somente a faixa de vídeo, sem áudio."),
                DownloadOption("audio-mp3", "Áudio", "MP3", "Converte o melhor áudio disponível para MP3.", audio_format="mp3"),
                DownloadOption("audio-m4a", "Áudio", "M4A", "Converte o melhor áudio disponível para M4A.", audio_format="m4a"),
                DownloadOption("audio-opus", "Áudio", "Opus", "Converte o melhor áudio disponível para Opus.", audio_format="opus"),
                DownloadOption("audio-original", "Áudio", "Original", "Mantém o melhor áudio no formato fornecido pela plataforma."),
            ]

        formats = info.get("formats") or []
        heights: dict[int, set[str]] = {}
        audio_exts: set[str] = set()
        has_audio = False
        for fmt in formats:
            vcodec = fmt.get("vcodec") or "none"
            acodec = fmt.get("acodec") or "none"
            ext = (fmt.get("ext") or "").lower()
            height = fmt.get("height")
            if acodec != "none":
                has_audio = True
            if vcodec != "none" and isinstance(height, int) and height > 0:
                heights.setdefault(height, set()).add(ext)
            if acodec != "none" and vcodec == "none" and ext:
                audio_exts.add(ext)

        result = [DownloadOption("best-av", "Completo", "Melhor qualidade", "Seleciona automaticamente a melhor combinação de vídeo + áudio.")]
        for h in sorted(heights, reverse=True):
            exts = [e for e in ("mp4", "webm") if e in heights[h]] + sorted(e for e in heights[h] if e not in {"mp4", "webm"})
            for ext in exts:
                result.append(DownloadOption(f"av-{h}-{ext}", "Completo", f"{h}p · {ext.upper()}", "Vídeo + áudio.", height=h, container=ext))
        for h in sorted(heights, reverse=True):
            exts = [e for e in ("mp4", "webm") if e in heights[h]] + sorted(e for e in heights[h] if e not in {"mp4", "webm"})
            for ext in exts:
                result.append(DownloadOption(f"video-{h}-{ext}", "Vídeo", f"{h}p · {ext.upper()}", "Somente vídeo, sem faixa de áudio.", height=h, container=ext))
        if has_audio:
            result.extend([
                DownloadOption("audio-mp3", "Áudio", "MP3", "Alta compatibilidade usando o melhor áudio disponível.", audio_format="mp3"),
                DownloadOption("audio-m4a", "Áudio", "M4A", "Boa qualidade com arquivo compacto.", audio_format="m4a"),
                DownloadOption("audio-opus", "Áudio", "Opus", "Formato moderno e eficiente.", audio_format="opus"),
                DownloadOption("audio-original", "Áudio", "Original", "Mantém o melhor formato de áudio fornecido pela plataforma."),
            ])
            if "webm" in audio_exts:
                result.append(DownloadOption("audio-webm", "Áudio", "WebM", "Faixa WebM original disponível nesta mídia.", source_ext="webm"))
        return result

    def _apply_media_info(self, info: dict[str, Any], options: list[DownloadOption]) -> None:
        title = info.get("title") or "Mídia encontrada"
        uploader = info.get("uploader") or info.get("channel") or ""
        entries = info.get("entries") or []
        suffix = f" · {len(entries)} itens" if entries else ""
        self.media_title.configure(text=title)
        self.media_subtitle.configure(text=(uploader + suffix).strip(" ·") or "Formatos carregados")
        self.status_var.set("Pronto para baixar")
        self._refresh_option_menu()

    def _refresh_option_menu(self) -> None:
        group = self.group_var.get()
        filtered = [o for o in self.options if o.group == group]
        if not filtered:
            self.option_menu.configure(values=["Indisponível"])
            self.option_var.set("Indisponível")
            self.option_description.configure(text="Não há opções disponíveis nesse grupo para o link analisado.")
            return
        values = [o.label for o in filtered]
        self.option_menu.configure(values=values)
        self.option_var.set(values[0])
        self._update_option_description(values[0])

    def _selected_option(self) -> DownloadOption | None:
        group = self.group_var.get()
        label = self.option_var.get()
        return next((o for o in self.options if o.group == group and o.label == label), None)

    def _update_option_description(self, _: str) -> None:
        option = self._selected_option()
        self.option_description.configure(text=option.description if option else "Opção indisponível.")

    def download(self) -> None:
        url = self.url_var.get().strip()
        option = self._selected_option()
        if not url.startswith(("http://", "https://")):
            messagebox.showwarning(APP_NAME, "Cole e analise um link válido primeiro.")
            return
        if option is None:
            messagebox.showwarning(APP_NAME, "Escolha uma qualidade ou formato disponível.")
            return
        folder = Path(self.folder_var.get().strip() or default_download_folder())
        try:
            folder.mkdir(parents=True, exist_ok=True)
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Não foi possível usar a pasta escolhida:\n{exc}")
            return
        if self.busy:
            return

        self.cancel_event.clear()
        self.success_card.grid_remove()
        self.progress_var.set(0.0)
        self._set_busy(True, "Preparando download…")
        threading.Thread(target=self._download_worker, args=(url, option, folder), daemon=True).start()

    def _download_worker(self, url: str, option: DownloadOption, folder: Path) -> None:
        try:
            ydl_opts = self._ydl_options(option, folder)
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([url])
            if self.cancel_event.is_set():
                raise CancelledDownload()
            self._ui(self._download_success, folder)
        except CancelledDownload:
            self._ui(self._download_cancelled)
        except Exception as exc:
            traceback.print_exc()
            self._ui(self._show_error, self._friendly_error(exc))
        finally:
            self._ui(self._set_busy, False, None)

    def _ydl_options(self, option: DownloadOption, folder: Path) -> dict[str, Any]:
        is_playlist = bool((self.media_info or {}).get("entries"))
        outtmpl = str(folder / ("%(playlist_index)02d - %(title).46s.%(ext)s" if is_playlist else "%(title).60s.%(ext)s"))
        ffmpeg_dir = bundled_ffmpeg_dir()

        opts: dict[str, Any] = {
            "outtmpl": outtmpl,
            "noplaylist": False,
            "ignoreerrors": is_playlist,
            "continuedl": True,
            "noprogress": True,
            "quiet": True,
            "no_warnings": True,
            "progress_hooks": [self._progress_hook],
            "windowsfilenames": True,
            "overwrites": False,
        }
        if ffmpeg_dir:
            opts["ffmpeg_location"] = ffmpeg_dir

        if option.key == "best-av":
            opts["format"] = "bestvideo+bestaudio/best"
            opts["merge_output_format"] = "mp4"
        elif option.key == "playlist-mp4":
            opts["format"] = "bestvideo[ext=mp4]+bestaudio/best[ext=mp4]/best"
            opts["merge_output_format"] = "mp4"
        elif option.key == "video-best":
            opts["format"] = "bestvideo"
        elif option.group == "Completo" and option.height:
            ext = option.container or "mp4"
            opts["format"] = f"bestvideo[height<={option.height}][ext={ext}]+bestaudio/best[height<={option.height}][ext={ext}]/best[height<={option.height}]"
            opts["merge_output_format"] = ext if ext in {"mp4", "webm", "mkv"} else "mp4"
        elif option.group == "Vídeo" and option.height:
            ext = option.container or "mp4"
            opts["format"] = f"bestvideo[height<={option.height}][ext={ext}]/bestvideo[height<={option.height}]"
        elif option.audio_format:
            opts["format"] = "bestaudio/best"
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": option.audio_format,
                "preferredquality": "0",
            }]
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
            progress = downloaded / total if total else 0.0
            speed = clean_status(data.get("_speed_str") or "")
            eta = clean_status(data.get("_eta_str") or "")
            text = "Baixando…"
            if speed:
                text += f" · {speed}"
            if eta:
                text += f" · {eta} restantes"
            self._ui(self.progress_var.set, max(0.0, min(progress, 1.0)))
            self._ui(self.status_var.set, text)
        elif status == "finished":
            self._ui(self.progress_var.set, 1.0)
            self._ui(self.status_var.set, "Processando arquivo…")

    def cancel_download(self) -> None:
        if self.busy:
            self.cancel_event.set()
            self.status_var.set("Cancelando…")

    def _download_success(self, folder: Path) -> None:
        self.folder_var.set(str(folder))
        self.progress_var.set(1.0)
        self.status_var.set("Download concluído com sucesso")
        self.success_label.configure(text="Download concluído com sucesso.")
        self.success_card.grid()

    def _download_cancelled(self) -> None:
        self.progress_var.set(0.0)
        self.status_var.set("Download cancelado")

    def open_folder(self) -> None:
        folder = Path(self.folder_var.get().strip())
        try:
            folder.mkdir(parents=True, exist_ok=True)
            os.startfile(str(folder))
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Não foi possível abrir a pasta:\n{exc}")

    def _show_error(self, message: str) -> None:
        self.progress_var.set(0.0)
        self.status_var.set("Não foi possível concluir a operação")
        messagebox.showerror(APP_NAME, message)

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
        if isinstance(exc, CancelledDownload):
            return "Download cancelado."
        return raw or "Não foi possível concluir a operação."


if __name__ == "__main__":
    app = GetMuviApp()
    app.mainloop()
