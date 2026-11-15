"""Пишет JSON ресурспака: pack.mcmeta, модели кнопок и узлов древа. Шрифт пишет tools/GenArt.java.
Запуск: python tools/gen_pack.py (после GenArt)."""
import json, os

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, '..', 'src', 'main', 'pack')
A = os.path.join(ROOT, 'assets', 'cascade')
TREE = os.path.join(HERE, '..', 'src', 'main', 'resources', 'tree.txt')


def w(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


w(os.path.join(ROOT, 'pack.mcmeta'), {'pack': {
    'description': 'Каскад: меню навыков', 'pack_format': 97, 'min_format': 97, 'max_format': 97}})

# Простые кнопки-картинки
for name in ('btn_reset', 'btn_left', 'btn_right', 'emblem'):
    w(os.path.join(A, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'cascade:item/{name}'}})
    w(os.path.join(A, 'models', 'item', name + '.json'),
      {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'cascade:item/{name}'}})

# Невидимый предмет: только подсказка при наведении (на круглой карте)
w(os.path.join(A, 'items', 'blank.json'), {'model': {'type': 'minecraft:empty'}})

# Узлы древа в клетках: цветное кольцо (tint из custom_model_data) + уменьшенная иконка ванильного предмета.
icons = {}
for line in open(TREE, encoding='utf-8'):
    t = line.split()
    if t and t[0] == 'icon':
        icons[t[1]] = t[2]

cmd = {'type': 'minecraft:custom_model_data', 'index': 0, 'default': -1}
w(os.path.join(A, 'models', 'item', 'node_ring.json'), {'parent': 'minecraft:item/generated',
    'textures': {'layer0': 'cascade:item/disc_fill', 'layer1': 'cascade:item/disc_hole'},
    'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, -2], 'scale': [0.95, 0.95, 0.95]}}})
for name, model in icons.items():
    block = model.startswith('block/')
    gui = {'rotation': [30, 225, 0], 'translation': [0, 0, 2], 'scale': [0.38, 0.38, 0.38]} if block \
        else {'rotation': [0, 0, 0], 'translation': [0, 0, 2], 'scale': [0.55, 0.55, 0.55]}
    w(os.path.join(A, 'models', 'item', 'icon_' + name + '.json'), {'parent': 'minecraft:' + model, 'display': {'gui': gui}})
    w(os.path.join(A, 'items', 'skill_' + name + '.json'), {'model': {'type': 'minecraft:composite', 'models': [
        {'type': 'minecraft:model', 'model': 'cascade:item/node_ring', 'tints': [cmd, {'type': 'minecraft:constant', 'value': -1}]},
        {'type': 'minecraft:model', 'model': 'cascade:item/icon_' + name}]}})

# Подпись «Инвентарь» рисуется поверх фона — прячем её.
for lang in ('en_us', 'ru_ru'):
    w(os.path.join(ROOT, 'assets', 'minecraft', 'lang', lang + '.json'), {'container.inventory': ''})
print('icons:', len(icons))
