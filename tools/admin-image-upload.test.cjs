const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');

// DOM と通信を置き換え、確認前・失敗時に画像パスが変更されないことを確認する。
function page(fetch) {
    class Element {
        constructor() { this.handlers = {}; this.disabled = false; this.files = []; this._value = ''; }
        addEventListener(type, listener) { this.handlers[type] = listener; }
        get value() { return this._value; }
        set value(value) { this._value = value; if (value === '') this.files = []; }
        getAttribute(name) { return this[name]; }
    }
    const ids = ['image-upload-controls', 'product-image-file', 'product-image-preview',
        'upload-product-image', 'confirm-product-image', 'discard-product-image', 'image-upload-status'];
    const elements = Object.fromEntries(ids.map(id => [id, new Element()]));
    const form = new Element();
    const path = new Element();
    path.value = '/images/products/knit1.png';
    form.querySelector = selector => selector.includes('imageUrl') ? path : { value: 'csrf-token' };
    const controls = elements['image-upload-controls'];
    controls.closest = () => form;
    controls.dataset = { uploadUrl: '/luna/admin/product-images', context: '/luna/',
        csrfHeader: 'X-CSRF-TOKEN', fallback: '/luna/images/common/no-image.svg' };
    runInNewContext(readFileSync(resolve(__dirname, '../src/main/resources/static/js/admin-image-upload.js'), 'utf8'), {
        document: { getElementById: id => elements[id] }, fetch,
        FormData: class { append() {} },
    });
    const picker = elements['product-image-file'];
    picker.files = [{ size: 100, type: 'image/png' }];
    picker.handlers.change();
    return { elements, path, form, picker };
}

test('upload requires preview confirmation and final product save', async () => {
    const newPath = '/images/uploads/' + 'a'.repeat(32) + '.png';
    let request;
    const ui = page(async (url, options) => {
        request = { url, options };
        return { ok: true, json: async () => ({ imageUrl: newPath }) };
    });
    let blocked = false;
    ui.form.handlers.submit({ preventDefault: () => { blocked = true; } });
    assert.equal(blocked, true);
    await ui.elements['upload-product-image'].handlers.click();
    assert.equal(ui.path.value, '/images/products/knit1.png');
    assert.equal(ui.elements['product-image-preview'].src, '/luna' + newPath);
    assert.equal(request.options.headers['X-CSRF-TOKEN'], 'csrf-token');
    assert.equal(request.url, '/luna/admin/product-images');
    ui.elements['confirm-product-image'].handlers.click();
    assert.equal(ui.path.value, newPath);
    assert.equal(ui.picker.files.length, 0);
    blocked = false;
    ui.form.handlers.submit({ preventDefault: () => { blocked = true; } });
    assert.equal(blocked, false);
});

test('failed upload retains original path and prevents accidental product save', async () => {
    const ui = page(async () => ({ ok: false, json: async () => ({ message: 'invalid image' }) }));
    await ui.elements['upload-product-image'].handlers.click();
    assert.equal(ui.path.value, '/images/products/knit1.png');
    assert.equal(ui.elements['confirm-product-image'].disabled, true);
    assert.equal(ui.elements['image-upload-status'].textContent, 'invalid image');
    let blocked = false;
    ui.form.handlers.submit({ preventDefault: () => { blocked = true; } });
    assert.equal(blocked, true);
    ui.elements['discard-product-image'].handlers.click();
    assert.equal(ui.path.value, '/images/products/knit1.png');
    assert.equal(ui.picker.files.length, 0);
});

test('in-flight upload blocks saving and cancellation', async () => {
    let complete;
    const ui = page(() => new Promise(resolve => { complete = resolve; }));
    const upload = ui.elements['upload-product-image'].handlers.click();
    assert.equal(ui.picker.disabled, true);
    assert.equal(ui.elements['discard-product-image'].disabled, true);
    let blocked = false;
    ui.form.handlers.submit({ preventDefault: () => { blocked = true; } });
    assert.equal(blocked, true);
    complete({ ok: false, json: async () => ({ message: 'failed' }) });
    await upload;
    assert.equal(ui.picker.disabled, false);
    assert.equal(ui.path.value, '/images/products/knit1.png');
});
