// 読み込みに失敗した画像だけを差し替え、画面構成と既存の表示サイズを維持する。
(() => {
    const currentScript = document.currentScript;
    const fallback = currentScript.dataset.fallback;

    function replace(image) {
        if (!(image instanceof HTMLImageElement) || !image.hasAttribute("data-product-image")
                || image.dataset.fallbackApplied === "true") return;
        // 代替画像も取得できない場合は再試行せず、代替テキストを表示する。
        image.dataset.fallbackApplied = "true";
        image.src = fallback;
    }

    document.addEventListener("error", event => replace(event.target), true);
    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("img[data-product-image]").forEach(image => {
            if (image.complete && image.naturalWidth === 0) replace(image);
        });
    });
})();
