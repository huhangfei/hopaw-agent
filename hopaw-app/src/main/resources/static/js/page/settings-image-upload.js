// 设置 - 图片上传 tab：配置图片压缩开关与按文件大小分档的压缩质量
var SETTINGS_KEYS = [
    'image_upload_compress_enabled',
    'image_compress_tiers'
];

// 默认分档（与后端默认一致）：100KB→80%、512KB→60%、1024KB→40%
var DEFAULT_IMAGE_TIERS = [
    { minSize: 100, quality: 80 },
    { minSize: 512, quality: 60 },
    { minSize: 1024, quality: 40 }
];

function onSettingsLoaded() {
    var enabledEl = document.getElementById('imageCompressEnabled');
    enabledEl.checked = settingsCache['image_upload_compress_enabled'] === '1';
    updateCompressEnabledLabel();
    enabledEl.addEventListener('change', updateCompressEnabledLabel);

    var tiers = parseTiers(settingsCache['image_compress_tiers']);
    renderTiers(tiers.length ? tiers : DEFAULT_IMAGE_TIERS);
}

function updateCompressEnabledLabel() {
    document.getElementById('imageCompressEnabledLabel').textContent =
        document.getElementById('imageCompressEnabled').checked ? '开启' : '关闭';
}

function parseTiers(raw) {
    if (!raw) return [];
    try {
        var arr = JSON.parse(raw);
        if (!Array.isArray(arr)) return [];
        return arr.filter(function(t) {
            return t && Number(t.minSize) > 0 && Number(t.quality) >= 1 && Number(t.quality) <= 100;
        }).map(function(t) {
            return { minSize: parseInt(t.minSize, 10), quality: parseInt(t.quality, 10) };
        });
    } catch (e) {
        return [];
    }
}

function renderTiers(tiers) {
    var container = document.getElementById('imageCompressTiers');
    container.innerHTML = '';
    tiers.forEach(function(t) { container.appendChild(createTierRow(t.minSize, t.quality)); });
}

function createTierRow(minSize, quality) {
    var row = document.createElement('div');
    row.className = 'image-tier-row';

    var minInput = document.createElement('input');
    minInput.type = 'number';
    minInput.className = 'settings-input image-tier-min-size';
    minInput.min = '1';
    minInput.step = '1';
    minInput.placeholder = '100';
    minInput.value = minSize || '';
    row.appendChild(minInput);

    var unit = document.createElement('span');
    unit.className = 'image-tier-unit';
    unit.textContent = 'KB 起';
    row.appendChild(unit);

    var qualityInput = document.createElement('input');
    qualityInput.type = 'range';
    qualityInput.className = 'image-tier-quality';
    qualityInput.min = '10';
    qualityInput.max = '100';
    qualityInput.step = '5';
    qualityInput.value = quality || 80;
    qualityInput.addEventListener('input', function() {
        qualityValue.textContent = qualityInput.value + '%';
    });
    row.appendChild(qualityInput);

    var qualityValue = document.createElement('span');
    qualityValue.className = 'image-tier-quality-value';
    qualityValue.textContent = qualityInput.value + '%';
    row.appendChild(qualityValue);

    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn-remove-key';
    removeBtn.textContent = '×';
    removeBtn.title = '删除该分档';
    removeBtn.onclick = function() { row.remove(); };
    row.appendChild(removeBtn);

    return row;
}

window.addCompressTier = function () {
    document.getElementById('imageCompressTiers').appendChild(createTierRow('', 80));
};

function collectTiers() {
    var rows = document.querySelectorAll('#imageCompressTiers .image-tier-row');
    var tiers = [];
    for (var i = 0; i < rows.length; i++) {
        var minSize = parseInt(rows[i].querySelector('.image-tier-min-size').value, 10);
        var quality = parseInt(rows[i].querySelector('.image-tier-quality').value, 10);
        if (isNaN(minSize) || minSize < 1) {
            showToast('第 ' + (i + 1) + ' 行分档的最小大小应填正整数（KB）', 'error');
            return null;
        }
        if (isNaN(quality) || quality < 10 || quality > 100) {
            showToast('第 ' + (i + 1) + ' 行分档的压缩质量应在 10~100 之间', 'error');
            return null;
        }
        tiers.push({ minSize: minSize, quality: quality });
    }
    if (tiers.length === 0) {
        showToast('请至少保留一个压缩分档', 'error');
        return null;
    }
    // 校验最小大小不重复
    var seen = {};
    for (var j = 0; j < tiers.length; j++) {
        if (seen[tiers[j].minSize]) {
            showToast('分档最小大小 ' + tiers[j].minSize + 'KB 重复，请调整', 'error');
            return null;
        }
        seen[tiers[j].minSize] = true;
    }
    // 按最小大小升序排序后保存，便于阅读
    tiers.sort(function(a, b) { return a.minSize - b.minSize; });
    return tiers;
}

window.saveImageUploadSettings = function () {
    var tiers = collectTiers();
    if (tiers === null) return;

    var enabled = document.getElementById('imageCompressEnabled').checked ? '1' : '0';

    var saves = [];
    saves.push(saveConfig('image_upload_compress_enabled', enabled, '是否开启图片压缩（1=开启 0=关闭）'));
    saves.push(saveConfig('image_compress_tiers', JSON.stringify(tiers), '图片压缩分档设置 [{minSize(KB),quality(1-100)}]'));

    Promise.all(saves).then(function(results) {
        if (results.every(function(r) { return r; })) {
            showToast('图片上传设置已保存，下次上传生效', 'success');
        } else {
            showToast('部分配置保存失败', 'error');
        }
    });
};
