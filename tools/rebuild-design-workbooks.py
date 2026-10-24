"""Split design documents while retaining original Excel styles and screenshots.

Uses only the Python standard library. Inputs are never overwritten.
"""
from pathlib import Path
import copy
import hashlib
import json
import math
import posixpath
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '設計書_分冊版'
S = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
P = 'http://schemas.openxmlformats.org/package/2006/relationships'
NS = {'s': S}
ET.register_namespace('', S)
ET.register_namespace('r', R)


def xml(data):
    return ET.fromstring(data)


def dump(root):
    # ElementTree can drop unused namespace declarations named by mc:Ignorable.
    for el in root.iter():
        el.attrib.pop('{http://schemas.openxmlformats.org/markup-compatibility/2006}Ignorable', None)
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def read(path):
    with zipfile.ZipFile(path) as z:
        return {n: z.read(n) for n in z.namelist() if not n.endswith('/')}


def strings(parts):
    if 'xl/sharedStrings.xml' not in parts:
        return []
    return [''.join(e.itertext()) for e in xml(parts['xl/sharedStrings.xml']).findall('s:si', NS)]


def text(cell, shared):
    v = cell.find('s:v', NS)
    if cell.get('t') == 's':
        return shared[int(v.text)] if v is not None else ''
    inline = cell.find('s:is', NS)
    return ''.join(inline.itertext()) if inline is not None else v.text if v is not None else ''


def sheet_paths(parts):
    rels = {x.get('Id'): posixpath.normpath(posixpath.join('xl', x.get('Target').lstrip('/'))) if not x.get('Target').startswith('/') else x.get('Target').lstrip('/') for x in xml(parts['xl/_rels/workbook.xml.rels'])}
    return [(s.get('name'), rels[s.get('{' + R + '}id')]) for s in xml(parts['xl/workbook.xml']).find('s:sheets', NS)]


def restyle(parts, original, kind):
    result = dict(parts)
    # Reuse the original style table, including its fonts, colors and borders.
    result['xl/styles.xml'] = original['xl/styles.xml']
    if 'xl/theme/theme1.xml' in original:
        result['xl/theme/theme1.xml'] = original['xl/theme/theme1.xml']
    mapping = {
        '要件定義書': {0: 0, 1: 10, 2: 3, 3: 3, 4: 4, 5: 3},
        '基本設計書': {0: 0, 1: 56, 2: 58, 3: 58, 4: 54, 5: 58},
        '詳細設計書': {0: 0, 1: 5, 2: 4, 3: 4, 4: 2, 5: 4},
    }[kind]
    shared = strings(parts)
    for name, path in sheet_paths(parts):
        root = xml(parts[path])
        widths = {}
        for col in root.findall('s:cols/s:col', NS):
            for i in range(int(col.get('min')), int(col.get('max')) + 1):
                widths[i] = float(col.get('width', '12'))
        for row in root.findall('s:sheetData/s:row', NS):
            lines = 1
            for cell in row.findall('s:c', NS):
                cell.set('s', str(mapping[int(cell.get('s', '0'))]))
                col_letters = re.match('[A-Z]+', cell.get('r')).group()
                col_num = 0
                for ch in col_letters:
                    col_num = col_num * 26 + ord(ch) - 64
                value = text(cell, shared)
                if row.get('r') not in ('1', '2'):
                    width = max(8, widths.get(col_num, 12) - 2)
                    lines = max(lines, sum(max(1, math.ceil(sum(2 if ord(c) > 255 else 1 for c in line) / width)) for line in value.split('\n')))
            row.set('ht', str(max(32 if row.get('r') == '1' else 25, lines * 16 + 10)))
            row.set('customHeight', '1')
        view = root.find('s:sheetViews/s:sheetView', NS)
        if view is not None:
            view.set('showGridLines', '0')
        result[path] = dump(root)
    return result


def subset(parts, selected):
    result = dict(parts)
    wb = xml(parts['xl/workbook.xml'])
    sheets = wb.find('s:sheets', NS)
    old_sheets = list(sheets)
    keep_ids = set()
    index_map = {}
    for i, sheet in enumerate(old_sheets):
        if sheet.get('name') in selected:
            index_map[i] = len(keep_ids)
            keep_ids.add(sheet.get('{' + R + '}id'))
        else:
            sheets.remove(sheet)
    assert len(keep_ids) == len(selected), selected
    for view in wb.findall('s:bookViews/s:workbookView', NS):
        view.set('activeTab', '0')
        view.set('firstSheet', '0')
    names = wb.find('s:definedNames', NS)
    if names is not None:
        for entry in list(names):
            idx = entry.get('localSheetId')
            if idx is not None:
                if int(idx) not in index_map:
                    names.remove(entry)
                else:
                    entry.set('localSheetId', str(index_map[int(idx)]))
    result['xl/workbook.xml'] = dump(wb)
    rels = xml(parts['xl/_rels/workbook.xml.rels'])
    for rel in list(rels):
        if rel.get('Type').endswith('/worksheet') and rel.get('Id') not in keep_ids:
            rels.remove(rel)
    result['xl/_rels/workbook.xml.rels'] = dump(rels)
    # Keep only package parts reachable through OPC relationships.
    reachable = {'[Content_Types].xml'}
    pending = ['']
    while pending:
        part = pending.pop()
        if part:
            reachable.add(part)
        relpath = posixpath.join(posixpath.dirname(part), '_rels', posixpath.basename(part) + '.rels') if part else '_rels/.rels'
        if relpath not in result or relpath in reachable:
            continue
        reachable.add(relpath)
        for rel in xml(result[relpath]):
            if rel.get('TargetMode') == 'External':
                continue
            target = rel.get('Target')
            target = target.lstrip('/') if target.startswith('/') else posixpath.normpath(posixpath.join(posixpath.dirname(part), target))
            assert target in result, (part, target)
            if target not in reachable:
                pending.append(target)
    result = {p: b for p, b in result.items() if p in reachable}
    ct = xml(result['[Content_Types].xml'])
    for entry in list(ct):
        if entry.tag.endswith('Override') and entry.get('PartName').lstrip('/') not in result:
            ct.remove(entry)
    result['[Content_Types].xml'] = dump(ct)
    # Rebuild application metadata rather than retaining the previous sheet list.
    if 'docProps/app.xml' in result:
        app_ns = 'http://schemas.openxmlformats.org/officeDocument/2006/extended-properties'
        app = ET.Element('{' + app_ns + '}Properties')
        ET.SubElement(app, '{' + app_ns + '}Application').text = 'LUNA'
        result['docProps/app.xml'] = dump(app)
    return result


def write(path, parts):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, content in parts.items():
            z.writestr(name, content)


GROUPS = {
    '要件定義書': [
        ('01_業務・機能要件', [0, 1, 2, 3, 4, 5]),
        ('02_非機能・データ・制約', [6, 7, 8]),
        ('03_受入条件・要件対応', [9, 10]),
    ],
    '基本設計書': [
        ('01_システム・機能・画面構成', [0, 1, 2, 3, 4, 5]),
        ('03_画面項目・表示・権限', [6, 7, 8]),
        ('04_業務・DB・運用設計', [9, 10, 11, 12, 13, 14]),
    ],
    '詳細設計書': [
        ('01_共通・認証・クラス設計', [0, 1, 2, 3, 4, 5, 14, 15]),
        ('02_商品・カート・注文処理', [6, 7, 8, 9, 10, 11, 12]),
        ('03_DB・Repository・URL設計', [13, 16, 17, 18, 19]),
        ('04_シーケンス・テスト・対応表', [20, 21, 22]),
    ],
}


def main():
    records = []
    for kind, groups in GROUPS.items():
        source = ROOT / kind / f'LUNA_{kind}_許.xlsx'
        original_path = ROOT / kind / f'LUNA_{kind}_許_改訂前_20261024.xlsx'
        current, original = read(source), read(original_path)
        styled = restyle(current, original, kind)
        names = [n for n, p in sheet_paths(current)]
        for label, indices in groups:
            selected = [names[i] for i in indices]
            path = OUT / kind / f'LUNA_{kind}_{label}_許.xlsx'
            parts = subset(styled, selected)
            write(path, parts)
            records.append({'file': str(path.relative_to(OUT)), 'sheets': selected, 'source': str(source.relative_to(ROOT)), 'images': 0})
        if kind == '基本設計書':
            selected = ['06_ログイン画面設計', '07_ホーム画面設計']
            parts = subset(original, selected)
            # Screen XML, drawings, anchors, image bytes and style table stay intact.
            for name, path in sheet_paths(parts):
                assert parts[path] == original[path]
            images = [p for p in parts if p.startswith('xl/media/')]
            assert len(images) == 2
            for image in images:
                assert hashlib.sha256(parts[image]).digest() == hashlib.sha256(original[image]).digest()
            path = OUT / kind / 'LUNA_基本設計書_02_画面設計・原版スクリーンショット_許.xlsx'
            write(path, parts)
            records.append({'file': str(path.relative_to(OUT)), 'sheets': selected, 'source': str(original_path.relative_to(ROOT)), 'images': len(images), 'note': '原版画面の参考資料。現行画面の項目・入力・表示仕様は03分冊を参照。'})
    lines = ['# LUNA 設計書・分冊版', '',
             '要件定義書、基本設計書、詳細設計書を用途別に分冊しました。文書本文は日本語です。', '',
             '各文書の改訂前ファイルからフォント・配色・罫線を再利用し、改訂版の内容をテーマごとに分割しています。元ファイルは変更していません。', '',
             '基本設計書02には原版のログイン画面とホーム画面のスクリーンショット2点を埋め込み、画像・配置・画面説明を保持しています。これらは原版画面の参考資料であり、現行サイトの撮り直し画像ではありません。現行仕様は基本設計書03を参照してください。', '',
             '分冊後もシート名・要件ID・機能IDを保持しています。他の分冊のシート名への言及は、以下の一覧から参照してください。', '',
             '| Excel | 収録シート |', '| --- | --- |']
    for r in sorted(records, key=lambda r: r['file']):
        lines.append(f"| [{Path(r['file']).name}]({r['file'].replace(chr(92), '/')}) | {'、'.join(r['sheets'])} |")
    (OUT / 'README.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    (OUT / '分冊一覧.json').write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    # Validate every generated XML and relationship, and check full sheet coverage.
    for r in records:
        p = OUT / r['file']
        parts = read(p)
        for n, b in parts.items():
            if n.endswith(('.xml', '.rels')):
                xml(b)
        assert [n for n, _ in sheet_paths(parts)] == r['sheets']
        subset(parts, r['sheets'])  # Also checks every internal relationship target.
        style_count = len(xml(parts['xl/styles.xml']).find('s:cellXfs', NS))
        for _, sp in sheet_paths(parts):
            for c in xml(parts[sp]).findall('.//s:c', NS):
                assert int(c.get('s', '0')) < style_count
        with zipfile.ZipFile(p) as z:
            assert z.testzip() is None
    for kind in GROUPS:
        source_sheets = {n for n, _ in sheet_paths(read(ROOT / kind / f'LUNA_{kind}_許.xlsx'))}
        actual = [s for r in records if r['source'] == str(Path(kind) / f'LUNA_{kind}_許.xlsx') for s in r['sheets']]
        assert len(actual) == len(set(actual)) and set(actual) == source_sheets
    print(json.dumps({'workbooks': len(records), 'screenshots_preserved': 2, 'validation': 'XML, ZIP, relationship targets, style indices, complete sheet coverage, original screenshot hashes and anchors passed'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
