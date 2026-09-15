from pathlib import Path
root = Path(__file__).resolve().parent
source = (root / 'source.html').read_text(encoding='utf-8')
source = source.replace('<link rel="stylesheet" href="styles.css">', '<style>' + (root / 'styles.css').read_text(encoding='utf-8') + '</style>')
source = source.replace('<script src="app.js"></script>', '<script>' + (root / 'app.js').read_text(encoding='utf-8') + '</script>')
(root / 'index.html').write_text(source, encoding='utf-8')
print('Updated index.html')
