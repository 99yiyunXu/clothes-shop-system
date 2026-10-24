// アップロードと商品保存を分離し、確認前の画像で商品情報を上書きしない。
(() => {
    const controls = document.getElementById('image-upload-controls');
    if (!controls) return;
    const form = controls.closest('form');
    const path = form.querySelector('[name="imageUrl"]');
    const picker = document.getElementById('product-image-file');
    const preview = document.getElementById('product-image-preview');
    const upload = document.getElementById('upload-product-image');
    const confirm = document.getElementById('confirm-product-image');
    const discard = document.getElementById('discard-product-image');
    const status = document.getElementById('image-upload-status');
    const context = controls.dataset.context.replace(/\/$/, '');
    let pending = null;
    let busy = false;

    function show(message, failed = false) {
        status.textContent = message;
        status.className = failed ? 'error' : 'muted';
    }
    function reset() {
        pending = null;
        picker.value = '';
        confirm.disabled = true;
        preview.src = path.value.startsWith('/images/') ? context + path.value : controls.dataset.fallback;
    }
    preview.addEventListener('error', () => {
        if (preview.getAttribute('src') !== controls.dataset.fallback) preview.src = controls.dataset.fallback;
    });
    picker.addEventListener('change', () => {
        pending = null;
        confirm.disabled = true;
        show('選択した画像をアップロードして確認してください。商品情報はまだ変更されていません。');
    });
    upload.addEventListener('click', async () => {
        const file = picker.files[0];
        if (!file || picker.files.length !== 1 || file.size === 0 || file.size > 5 * 1024 * 1024) {
            show('5 MB以下の画像を1件選択してください。', true);
            return;
        }
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
            show('JPG・PNG・WebP の画像を選択してください。', true);
            return;
        }
        busy = true;
        pending = null;
        upload.disabled = picker.disabled = discard.disabled = true;
        confirm.disabled = true;
        show('画像を確認・保存しています。');
        try {
            const data = new FormData();
            data.append('file', file);
            const csrf = form.querySelector('input[name="_csrf"]');
            const response = await fetch(controls.dataset.uploadUrl, {
                method: 'POST', body: data, credentials: 'same-origin',
                headers: { [controls.dataset.csrfHeader]: csrf.value, Accept: 'application/json' },
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.message || 'アップロードできません。ログイン状態、画像サイズと形式を確認してください。');
            if (!/^\/images\/uploads\/[0-9a-f]{32}\.(jpg|png)$/.test(result.imageUrl))
                throw new Error('画像の保存結果を確認できません。再度お試しください。');
            pending = result.imageUrl;
            preview.src = context + pending;
            confirm.disabled = false;
            show('画像を確認して「この画像を使用する」を押してください。商品はまだ保存されていません。');
        } catch (error) {
            show(error.message, true);
        } finally {
            busy = false;
            upload.disabled = picker.disabled = discard.disabled = false;
        }
    });
    confirm.addEventListener('click', () => {
        if (!pending || busy) return;
        path.value = pending;
        reset();
        show('画像パスに反映しました。最後に商品を登録・更新してください。');
    });
    discard.addEventListener('click', () => {
        if (busy) return;
        reset();
        show('画像の選択を取り消しました。商品情報は変更されていません。');
    });
    path.addEventListener('input', () => {
        if (busy) return;
        reset();
        show('入力した画像パスは商品保存時に確認されます。');
    });
    form.addEventListener('submit', event => {
        if (busy || pending || picker.files.length > 0) {
            event.preventDefault();
            show('画像をアップロードして使用を確定するか、選択を取り消してから商品を保存してください。', true);
        }
    });
})();
