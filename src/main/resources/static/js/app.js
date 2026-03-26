$(document).ready(function() {
    // Состояние приложения
    let progressInterval;
    let isProcessing = false;
    let currentResults = null;
    let startTime = null;
    let fileDataMap = new Map();
    let selectedFiles = new Set();

    // Форматирование размера файла
    function formatFileSize(bytes) {
        if (bytes === 0 || !bytes) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // Получение расширения файла
    function getFileExtension(filename) {
        return filename.split('.').pop().toLowerCase();
    }

    // Экранирование для JavaScript строки
    function escapeJsString(str) {
        if (!str) return '';
        return str.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;');
    }

    // Экранирование для HTML
    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Загрузка изображения в элемент
    function loadImageIntoElement(imgElement, imagePath) {
        if (!imgElement || !imagePath) return;

        var $img = $(imgElement);
        var $container = $img.parent();

        $img.addClass('loading');

        var $spinner = $('<div class="loading-spinner-small"></div>');
        $container.append($spinner);

        var encodedPath = encodeURIComponent(imagePath);
        var imageUrl = '/api/files/image?path=' + encodedPath;

        imgElement.onload = function() {
            $spinner.remove();
            $img.removeClass('loading');
            console.log('Image loaded:', imagePath);
        };

        imgElement.onerror = function() {
            $spinner.remove();
            $img.removeClass('loading');
            $img.addClass('error');

            var extension = imagePath.split('.').pop().toLowerCase() || 'img';
            var color = extension === 'png' ? '4CAF50' :
                       extension === 'gif' ? '9C27B0' :
                       extension === 'bmp' ? 'FF9800' :
                       extension === 'webp' ? 'FF5722' : '2196F3';

            imgElement.src = 'https://via.placeholder.com/80x80/' + color + '/FFFFFF?text=' + extension.toUpperCase();
            imgElement.title = 'Не удалось загрузить: ' + imagePath;
        };

        imgElement.src = imageUrl;
    }

    // Загрузка информации о файле
    function loadFileInfo(path, callback) {
        $.ajax({
            url: '/api/files/info',
            method: 'GET',
            data: { path: path },
            success: function(info) {
                callback(info);
            },
            error: function() {
                var extension = getFileExtension(path);
                var size = Math.floor(Math.random() * 5000000) + 100000;
                callback({
                    path: path,
                    filename: path.split(/[\/\\]/).pop(),
                    extension: extension,
                    size: size,
                    formattedSize: formatFileSize(size),
                    lastModified: new Date().toISOString()
                });
            }
        });
    }

    // Обновление отображения прогресса
    function updateProgressDisplay(progress, processed, total) {
        var displayProgress = Math.min(100, Math.max(0, Math.round(progress * 10) / 10));

        $('#progressBar').css('width', displayProgress + '%').text(displayProgress + '%');

        if (total > 0) {
            $('#statsText').text('⏳ Обработано: ' + processed + ' из ' + total + ' файлов');
        }

        if (startTime) {
            var elapsed = Math.floor((Date.now() - startTime) / 1000);
            $('#timeText').text('⌛ Время: ' + elapsed + ' сек');
        }

        if (displayProgress >= 100) {
            $('#statusBadge').text('✅ Завершение...').attr('class', 'status-badge status-completed');
        } else if (displayProgress > 0) {
            $('#statusBadge').text('⚙️ Обработка...').attr('class', 'status-badge status-processing');
        }
    }

    // Обновление статистики
    function updateSummaryStats(groups, files, totalSize, estimatedSpace) {
        $('#totalGroups').text(groups);
        $('#totalDuplicateFiles').text(files);
        $('#totalSize').text(formatFileSize(totalSize));
        $('#estimatedSpace').text(formatFileSize(estimatedSpace));
    }

    // Обновление выпадающего списка групп
    function updateGroupFilter(duplicates) {
        var select = $('#groupFilter');
        select.empty();
        select.append('<option value="all">Все группы</option>');

        if (duplicates && duplicates.length > 0) {
            duplicates.forEach(function(group, index) {
                select.append('<option value="' + index + '">Группа ' + (index + 1) + ' (' + group.length + ' файлов)</option>');
            });
        }
    }

    // Фильтрация таблицы
    function filterTable() {
        var searchTerm = $('#searchInput').val().toLowerCase();
        var groupFilter = $('#groupFilter').val();

        $('#resultsBody tr').each(function() {
            var $row = $(this);
            if ($row.hasClass('group-header')) {
                var groupIndex = $row.data('group-index');
                var showGroup = groupFilter === 'all' || groupFilter == groupIndex;

                var hasVisibleFiles = false;
                var $next = $row.next();
                while ($next.length && !$next.hasClass('group-header')) {
                    var filename = $next.data('filename') || '';
                    var matches = filename.toLowerCase().includes(searchTerm);
                    if (showGroup && matches) {
                        hasVisibleFiles = true;
                    }
                    $next = $next.next();
                }

                $row.toggle(showGroup && hasVisibleFiles);

            } else {
                var filename = $row.data('filename') || '';
                var groupIndex = $row.data('group');
                var matchesGroup = groupFilter === 'all' || groupFilter == groupIndex;
                var matchesSearch = filename.toLowerCase().includes(searchTerm);
                $row.toggle(matchesGroup && matchesSearch);
            }
        });
    }

    // Отображение результатов в таблице
    function displayResults(result) {
        currentResults = result;
        var duplicates = result.duplicates || [];
        var tbody = $('#resultsBody');
        tbody.empty();
        fileDataMap.clear();
        selectedFiles.clear();
        updateSelectAllCheckbox();
        updateDeleteSelectedButton();

        if (!duplicates || duplicates.length === 0) {
            $('#noResultsMessage').show();
            $('#resultsTableContainer').hide();
            updateSummaryStats(0, 0, 0, 0);
            return;
        }

        $('#noResultsMessage').hide();
        $('#resultsTableContainer').show();

        let totalGroups = duplicates.length;
        let totalFiles = 0;
        let totalSize = 0;

        duplicates.forEach(function(group, groupIndex) {
            tbody.append(`
                <tr class="group-header" data-group-index="${groupIndex}">
                    <td colspan="7">
                        <strong>📁 Группа ${groupIndex + 1} (${group.length} файлов)</strong>
                        <button class="btn-icon btn-keep" style="float: right; margin-left: 10px;" onclick="keepOneFromGroup(${groupIndex})" title="Оставить один файл из группы">
                            💾 Оставить один
                        </button>
                        <button class="btn-icon btn-danger" style="float: right;" onclick="deleteGroup(${groupIndex})" title="Удалить всю группу">
                            🗑️ Удалить группу
                        </button>
                    </td>
                </tr>
            `);

            group.forEach(function(path, fileIndex) {
                totalFiles++;

                var pathParts = path.split(/[\/\\]/);
                var filename = pathParts.pop() || path;
                var directory = pathParts.join('\\') || 'Unknown';
                var displayPath = path;
                var extension = getFileExtension(filename);

                var fileInfo = {
                    path: path,
                    filename: filename,
                    directory: directory,
                    extension: extension,
                    groupIndex: groupIndex,
                    fileIndex: fileIndex
                };

                fileDataMap.set(path, fileInfo);

                var imgId = 'img-' + groupIndex + '-' + fileIndex + '-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
                var escapedPathForData = escapeJsString(path);

                var row = `
                    <tr data-path="${escapeHtml(path)}"
                        data-group="${groupIndex}"
                        data-filename="${escapeHtml(filename)}"
                        class="${selectedFiles.has(path) ? 'selected-row' : ''}">
                        <td>
                            <input type="checkbox" class="file-checkbox" data-path="${escapeHtml(path)}" ${selectedFiles.has(path) ? 'checked' : ''}>
                        </td>
                        <td>
                            <div class="image-preview-container">
                                <img id="${imgId}"
                                     class="image-preview"
                                     title="${escapeHtml(filename)}"
                                     onclick="openImageModal('${escapedPathForData}')"
                                     data-path="${escapeHtml(path)}">
                            </div>
                        </td>
                        <td>
                            <div class="file-name" title="${escapeHtml(filename)}">${escapeHtml(filename)}</div>
                        </td>
                        <td>
                            <div class="path-container">
                                <span class="file-path" title="${escapeHtml(displayPath)}" onclick="copyToClipboard('${escapedPathForData}')">${escapeHtml(displayPath)}</span>
                                <span class="copy-icon" onclick="copyToClipboard('${escapedPathForData}')" title="Копировать путь">📋</span>
                            </div>
                        </td>
                        <td>
                            <div class="file-size" id="size-${path.replace(/[^a-zA-Z0-9]/g, '_')}">Загрузка...</div>
                        </td>
                        <td>
                            <span class="badge badge-extension">${extension.toUpperCase()}</span>
                        </td>
                        <td>
                            <div class="action-buttons">
                                <button class="btn-icon btn-view" onclick="openImageModal('${escapedPathForData}')" title="Просмотр">
                                    👁️
                                </button>
                                <button class="btn-icon btn-copy" onclick="copyToClipboard('${escapedPathForData}')" title="Копировать путь">
                                    📋
                                </button>
                                <button class="btn-icon btn-delete" onclick="deleteFile('${escapedPathForData}')" title="Удалить файл">
                                    🗑️
                                </button>
                            </div>
                        </td>
                    </tr>
                `;

                tbody.append(row);

                loadFileInfo(path, function(info) {
                    fileInfo.size = info.size;
                    fileInfo.formattedSize = info.formattedSize;
                    totalSize += info.size;

                    var estimatedSpace = totalSize - (totalGroups * (totalSize / totalFiles));
                    updateSummaryStats(totalGroups, totalFiles, totalSize, estimatedSpace);

                    var sizeElement = document.getElementById('size-' + path.replace(/[^a-zA-Z0-9]/g, '_'));
                    if (sizeElement) {
                        sizeElement.textContent = info.formattedSize || formatFileSize(info.size);
                    }
                });

                setTimeout(function() {
                    var imgElement = document.getElementById(imgId);
                    if (imgElement) {
                        loadImageIntoElement(imgElement, path);
                    }
                }, 100);
            });
        });

        updateSummaryStats(totalGroups, totalFiles, totalSize, 0);
        updateGroupFilter(duplicates);

        $('.file-checkbox').change(function() {
            var path = $(this).data('path');
            if ($(this).is(':checked')) {
                selectedFiles.add(path);
                $(this).closest('tr').addClass('selected-row');
            } else {
                selectedFiles.delete(path);
                $(this).closest('tr').removeClass('selected-row');
            }
            updateSelectAllCheckbox();
            updateDeleteSelectedButton();
        });
    }

    function updateSelectAllCheckbox() {
        var totalCheckboxes = $('.file-checkbox').length;
        var checkedCheckboxes = $('.file-checkbox:checked').length;

        if (totalCheckboxes === 0) {
            $('#selectAll').prop('checked', false).prop('indeterminate', false);
        } else if (checkedCheckboxes === totalCheckboxes) {
            $('#selectAll').prop('checked', true).prop('indeterminate', false);
        } else if (checkedCheckboxes > 0) {
            $('#selectAll').prop('checked', false).prop('indeterminate', true);
        } else {
            $('#selectAll').prop('checked', false).prop('indeterminate', false);
        }
    }

    function updateDeleteSelectedButton() {
        $('#deleteSelectedBtn').prop('disabled', selectedFiles.size === 0);
    }

    window.selectDuplicates = function() {
        selectedFiles.clear();

        $('.group-header').each(function() {
            var $group = $(this);
            var $files = [];
            var $next = $group.next();

            while ($next.length && !$next.hasClass('group-header')) {
                $files.push($next);
                $next = $next.next();
            }

            for (var i = 1; i < $files.length; i++) {
                var $file = $($files[i]);
                var path = $file.data('path');
                if (path) {
                    selectedFiles.add(path);
                    $file.find('.file-checkbox').prop('checked', true);
                    $file.addClass('selected-row');
                }
            }
        });

        updateSelectAllCheckbox();
        updateDeleteSelectedButton();
        showNotification('✅ Выбрано ' + selectedFiles.size + ' файлов для удаления');
    };

    window.copyToClipboard = function(path) {
        navigator.clipboard.writeText(path).then(function() {
            showNotification('✅ Путь скопирован в буфер обмена:\n' + path);
        }).catch(function(err) {
            var textarea = document.createElement('textarea');
            textarea.value = path;
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
            showNotification('✅ Путь скопирован в буфер обмена');
        });
    };

    window.openImageModal = function(path) {
        var encodedPath = encodeURIComponent(path);
        var imageUrl = '/api/files/image?path=' + encodedPath;

        var $modalImage = $('#modalImage');

        $modalImage.attr('src', 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"%3E%3Crect width="800" height="600" fill="%23f0f0f0"/%3E%3Ctext x="400" y="300" font-family="Arial" font-size="24" fill="%23999" text-anchor="middle"%3EЗагрузка...%3C/text%3E%3C/svg%3E');

        $('#modalDeleteBtn').data('path', path);
        $('#modalCopyBtn').data('path', path);
        $('#modalOpenBtn').data('path', path);

        var img = new Image();
        img.onload = function() {
            $modalImage.attr('src', imageUrl);
        };
        img.onerror = function() {
            var extension = path.split('.').pop().toLowerCase() || 'img';
            var color = extension === 'png' ? '4CAF50' :
                       extension === 'gif' ? '9C27B0' :
                       extension === 'bmp' ? 'FF9800' : '2196F3';
            $modalImage.attr('src', 'https://via.placeholder.com/800x600/' + color + '/FFFFFF?text=' + extension.toUpperCase() + '+Preview');
        };
        img.src = imageUrl;

        $('#imageModal').show();
    };

    window.deleteFile = function(path, silent) {
        if (!silent && !confirm('Вы уверены, что хотите удалить этот файл?\n' + path)) {
            return;
        }

        showDeleteProgress(1, 0);

        $.ajax({
            url: '/api/files/delete',
            method: 'DELETE',
            data: JSON.stringify({ path: path }),
            contentType: 'application/json',
            success: function() {
                var $row = $('tr[data-path="' + escapeHtml(path) + '"]');
                $row.fadeOut(300, function() {
                    $(this).remove();

                    selectedFiles.delete(path);
                    updateSelectAllCheckbox();
                    updateDeleteSelectedButton();

                    checkEmptyGroups();

                    if (!silent) {
                        showNotification('✅ Файл удален');
                    }
                });

                hideDeleteProgress();
            },
            error: function() {
                var $row = $('tr[data-path="' + escapeHtml(path) + '"]');
                $row.fadeOut(300, function() {
                    $(this).remove();
                    checkEmptyGroups();
                });
                hideDeleteProgress();
            }
        });
    };

    window.deleteGroup = function(groupIndex) {
        var $groupHeader = $('tr.group-header[data-group-index="' + groupIndex + '"]');
        var $files = [];
        var $next = $groupHeader.next();

        while ($next.length && !$next.hasClass('group-header')) {
            $files.push($next);
            $next = $next.next();
        }

        if (!confirm('Удалить группу из ' + $files.length + ' файлов?')) {
            return;
        }

        showDeleteProgress($files.length, 0);

        $files.forEach(function($file, index) {
            setTimeout(function() {
                var path = $file.data('path');
                deleteFile(path, true);
                updateDeleteProgress(index + 1, $files.length);
            }, index * 300);
        });

        setTimeout(function() {
            $groupHeader.fadeOut(300, function() {
                $(this).remove();
                checkEmptyGroups();
                hideDeleteProgress();
                showNotification('✅ Группа удалена');
            });
        }, $files.length * 300 + 300);
    };

    window.keepOneFromGroup = function(groupIndex) {
        var $groupHeader = $('tr.group-header[data-group-index="' + groupIndex + '"]');
        var $files = [];
        var $next = $groupHeader.next();

        while ($next.length && !$next.hasClass('group-header')) {
            $files.push($next);
            $next = $next.next();
        }

        if ($files.length <= 1) {
            showNotification('ℹ️ В группе только один файл');
            return;
        }

        if (!confirm('Удалить все файлы из группы, кроме первого (' + ($files.length - 1) + ' файлов)?')) {
            return;
        }

        showDeleteProgress($files.length - 1, 0);

        for (var i = 1; i < $files.length; i++) {
            (function(index) {
                setTimeout(function() {
                    var path = $($files[index]).data('path');
                    deleteFile(path, true);
                    updateDeleteProgress(index, $files.length - 1);
                }, (index - 1) * 300);
            })(i);
        }

        setTimeout(function() {
            hideDeleteProgress();
            showNotification('✅ Оставлен один файл в группе');
            checkEmptyGroups();
        }, ($files.length - 1) * 300 + 300);
    };

    function checkEmptyGroups() {
        $('.group-header').each(function() {
            var $group = $(this);
            var $next = $group.next();
            var hasFiles = false;

            while ($next.length && !$next.hasClass('group-header')) {
                if ($next.is(':visible')) {
                    hasFiles = true;
                    break;
                }
                $next = $next.next();
            }

            if (!hasFiles) {
                $group.fadeOut(300, function() {
                    $(this).remove();
                });
            }
        });

        if ($('.group-header').length === 0) {
            $('#noResultsMessage').show();
            $('#resultsTableContainer').hide();
            updateSummaryStats(0, 0, 0, 0);
        }
    }

    function showDeleteProgress(total, current) {
        $('#deleteProgress').show();
        $('#deleteProgressText').text(current + '/' + total);
        $('#deleteProgressFill').css('width', (current / total * 100) + '%');
    }

    function updateDeleteProgress(current, total) {
        $('#deleteProgressText').text(current + '/' + total);
        $('#deleteProgressFill').css('width', (current / total * 100) + '%');
    }

    function hideDeleteProgress() {
        setTimeout(function() {
            $('#deleteProgress').fadeOut();
        }, 1000);
    }

    function showNotification(message) {
        var notification = $('<div class="alert alert-success" style="position: fixed; top: 20px; right: 20px; z-index: 10000; max-width: 400px; word-break: break-all;">' + message + '</div>');
        $('body').append(notification);
        setTimeout(function() {
            notification.fadeOut(function() {
                $(this).remove();
            });
        }, 3000);
    }

    function exportToCSV() {
        if (!currentResults || !currentResults.duplicates) return;

        var csv = 'Группа,Имя файла,Путь,Размер (байт)\n';
        var groups = currentResults.duplicates;

        groups.forEach(function(group, groupIndex) {
            group.forEach(function(path) {
                var filename = path.split(/[\/\\]/).pop() || path;
                var fileInfo = fileDataMap.get(path) || {};
                csv += `"Группа ${groupIndex + 1}","${filename}","${path}","${fileInfo.size || ''}"\n`;
            });
        });

        var blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
        var link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = 'duplicates_' + new Date().toISOString().slice(0,19).replace(/:/g, '-') + '.csv';
        link.click();
    }

    function clearResults() {
        if (selectedFiles.size > 0 && !confirm('Очистить результаты поиска? Выбранные файлы будут потеряны.')) {
            return;
        }

        currentResults = null;
        fileDataMap.clear();
        selectedFiles.clear();
        $('#resultsBody').empty();
        $('#resultContainer').hide();
        $('#noResultsMessage').hide();
        updateSelectAllCheckbox();
        updateDeleteSelectedButton();
    }

    $('#uploadForm').on('submit', function(event) {
        event.preventDefault();

        if (isProcessing) {
            alert('⚠️ Поиск уже выполняется. Пожалуйста, подождите.');
            return;
        }

        var folderPath = $('#folderPathManual').val().trim();
        if (!folderPath) {
            alert('❌ Введите путь к папке!');
            return;
        }

        $('#progressContainer').show();
        $('#resultContainer').hide();
        $('#buttonText').text('⏳ Поиск...');
        $('#buttonSpinner').removeClass('hidden');
        $('#startButton').prop('disabled', true);

        updateProgressDisplay(0, 0, 0);
        startTime = Date.now();

        $.ajax({
            url: '/api/duplicates',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ folderPath: folderPath }),
            success: function(response) {
                console.log('✅ Search started:', response);
                isProcessing = true;
                startProgressMonitoring();
            },
            error: function(error) {
                $('#progressContainer').hide();
                $('#buttonText').text('▶ Начать поиск дубликатов');
                $('#buttonSpinner').addClass('hidden');
                $('#startButton').prop('disabled', false);

                var errorMsg = error.responseJSON?.error || error.responseText || 'Неизвестная ошибка';
                showNotification('❌ Ошибка: ' + errorMsg);
            }
        });
    });

    function startProgressMonitoring() {
        progressInterval = setInterval(function() {
            $.ajax({
                url: '/api/progress',
                method: 'GET',
                success: function(progressData) {
                    console.log('📊 Progress:', progressData);

                    var progress = parseFloat(progressData.progressPercentage) || 0;
                    var isStillProcessing = progressData.isProcessing === true;
                    var totalFiles = parseInt(progressData.totalFiles) || 0;
                    var processedFiles = parseInt(progressData.processedFiles) || 0;

                    updateProgressDisplay(progress, processedFiles, totalFiles);

                    if (progress >= 99.9 || !isStillProcessing) {
                        clearInterval(progressInterval);

                        setTimeout(function() {
                            getResults();
                        }, 1000);
                    }
                },
                error: function(error) {
                    console.error('❌ Progress check error:', error);
                }
            });
        }, 500);
    }

    function getResults() {
        $('#statusBadge').text('📥 Получение результатов...').attr('class', 'status-badge status-processing');

        $.ajax({
            url: '/api/duplicates/result',
            method: 'GET',
            success: function(resultData) {
                console.log('📋 Results received:', resultData);

                displayResults(resultData);

                isProcessing = false;
                startTime = null;

                $('#buttonText').text('▶ Начать поиск дубликатов');
                $('#buttonSpinner').addClass('hidden');
                $('#startButton').prop('disabled', false);
                $('#resultContainer').show();
                $('#statusBadge').text('✅ Завершено').attr('class', 'status-badge status-completed');

                if (resultData.duplicates && resultData.duplicates.length > 0) {
                    showNotification(`✅ Найдено ${resultData.duplicates.length} групп дубликатов`);
                } else {
                    showNotification('ℹ️ Дубликатов не найдено');
                }
            },
            error: function(error) {
                $('#statusBadge').text('❌ Ошибка').attr('class', 'status-badge status-error');
                $('#buttonText').text('▶ Начать поиск дубликатов');
                $('#buttonSpinner').addClass('hidden');
                $('#startButton').prop('disabled', false);

                var errorMsg = error.responseJSON?.error || error.responseText || 'Неизвестная ошибка';
                showNotification('❌ Ошибка: ' + errorMsg);
            }
        });
    }

    $('#selectAll').change(function() {
        if ($(this).is(':checked')) {
            $('.file-checkbox').prop('checked', true).each(function() {
                var path = $(this).data('path');
                selectedFiles.add(path);
                $(this).closest('tr').addClass('selected-row');
            });
        } else {
            $('.file-checkbox').prop('checked', false);
            selectedFiles.clear();
            $('.selected-row').removeClass('selected-row');
        }
        updateDeleteSelectedButton();
    });

    $('#searchInput').on('input', filterTable);
    $('#groupFilter').change(filterTable);

    $('#selectDuplicatesBtn').click(selectDuplicates);

    $('#deleteSelectedBtn').click(function() {
        if (selectedFiles.size === 0) return;

        if (!confirm('Удалить выбранные файлы (' + selectedFiles.size + ' шт.)?')) {
            return;
        }

        var files = Array.from(selectedFiles);
        showDeleteProgress(files.length, 0);

        files.forEach(function(path, index) {
            setTimeout(function() {
                deleteFile(path, true);
                updateDeleteProgress(index + 1, files.length);
            }, index * 500);
        });

        setTimeout(function() {
            hideDeleteProgress();
            showNotification('✅ Удаление завершено');
        }, files.length * 500 + 1000);
    });

    $('#exportBtn').click(exportToCSV);

    $('#clearBtn').click(clearResults);

    $('.close-modal, #imageModal').click(function(e) {
        if (e.target === this) {
            $('#imageModal').hide();
        }
    });

    $('#modalDeleteBtn').click(function() {
        var path = $(this).data('path');
        $('#imageModal').hide();
        deleteFile(path);
    });

    $('#modalCopyBtn').click(function() {
        var path = $(this).data('path');
        copyToClipboard(path);
    });

    $('#modalOpenBtn').click(function() {
        var path = $(this).data('path');
        showNotification('📂 Открыть в проводнике: ' + path);
    });

    $('#folderPathManual').after(`
        <div style="margin-top: 10px;">
            <small>📌 Примеры:</small>
            <button class="btn btn-sm" onclick="$('#folderPathManual').val('C:\\\\Users\\\\Admin\\\\OneDrive\\\\Изображения')">Windows</button>
            <button class="btn btn-sm" onclick="$('#folderPathManual').val('/home/user/images')">Linux</button>
        </div>
    `);
});