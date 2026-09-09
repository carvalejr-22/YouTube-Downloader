from pathlib import Path

path = Path("app/src/main/java/com/carlosvale/ytdownloader/MainActivity.kt")
text = path.read_text(encoding="utf-8")

old_title = '''                title = {
                    Column {
                        Text("GetMuvi", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Baixador de músicas e vídeos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
'''

new_title = '''                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Get",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "Muvi",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color(0xFF20F3E8),
                                            Color(0xFF1597FF),
                                            Color(0xFF765BFF),
                                            Color(0xFFF05CFF)
                                        )
                                    ),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                        Text(
                            "Baixador de músicas e vídeos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
'''

if old_title in text:
    text = text.replace(old_title, new_title, 1)
elif 'Text("GetMuvi", fontWeight = FontWeight.ExtraBold)' in text:
    raise SystemExit("Trecho do título GetMuvi mudou e não pôde ser atualizado com segurança.")

text = text.replace(
    'listOf(Color(0xFF00D5C7), Color(0xFF2188FF), Color(0xFF8B5CFF))',
    'listOf(Color(0xFF19E7DE), Color(0xFF098FFF), Color(0xFF654CFF), Color(0xFFE752FF))'
)
text = text.replace('shape = RoundedCornerShape(24.dp)', 'shape = RoundedCornerShape(28.dp)', 1)
text = text.replace('.background(gradient, RoundedCornerShape(24.dp))', '.background(gradient, RoundedCornerShape(28.dp))', 1)
text = text.replace('modifier = Modifier.size(68.dp)', 'modifier = Modifier.size(72.dp)', 1)

path.write_text(text, encoding="utf-8")
