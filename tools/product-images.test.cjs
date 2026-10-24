const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');

// 通信失敗とキャッシュ済みの失敗を再現し、代替画像への無限再試行を防ぐ。
test('failed product images fall back once without replacing valid images or banners', () => {
    class Image {
        constructor(product, complete, width) {
            this.product = product;
            this.complete = complete;
            this.naturalWidth = width;
            this.dataset = {};
            this.src = '/original.png';
        }
        hasAttribute(name) { return name === 'data-product-image' && this.product; }
    }
    const missing = new Image(true, true, 0);
    const valid = new Image(true, true, 900);
    const banner = new Image(false, true, 0);
    const listeners = {};
    const document = {
        currentScript: { dataset: { fallback: '/luna/images/common/no-image.svg' } },
        addEventListener: (type, listener) => { listeners[type] = listener; },
        querySelectorAll: () => [missing, valid],
    };
    runInNewContext(readFileSync(resolve(__dirname, '../src/main/resources/static/js/product-images.js'), 'utf8'),
        { document, HTMLImageElement: Image });
    listeners.DOMContentLoaded();
    assert.equal(missing.src, '/luna/images/common/no-image.svg');
    assert.equal(valid.src, '/original.png');
    listeners.error({ target: banner });
    assert.equal(banner.src, '/original.png');
    listeners.error({ target: valid });
    assert.equal(valid.src, '/luna/images/common/no-image.svg');
    valid.src = '/failed-fallback.svg';
    listeners.error({ target: valid });
    assert.equal(valid.src, '/failed-fallback.svg');
});
