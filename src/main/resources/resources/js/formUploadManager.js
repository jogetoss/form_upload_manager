(function ($) {
    var optionDefaults = {
        path: '',
        baseClass: 'fmBase',
        folderClass: 'fmFolder',
        loadingClass: 'fmLoading',
        highlightClass: 'ui-state-highlight',
        hoverClass: 'ui-state-active'
    };
    
    function sort (a, b) {
	if (a > b)
            return -1;
        if (a < b)
           return 1;
        return 0;
    }

    $.fn.formUploadManager = function (settings, pluploadOptions) {
        var mbOptions = $.extend({}, optionDefaults, settings);
        if (!mbOptions.ajaxPath) {
            alert('ajaxPath not specified');
            return;
        }
        pluploadOptions = $.extend({url: mbOptions.ajaxPath}, pluploadOptions);
        
        var frameId = "formUploadManagerFrame";

        var query = $.extend({}, {path: mbOptions.path}, mbOptions.get);
        this.each(function () { 
            var $sel = $(this);
            $.ajax({url: mbOptions.ajaxPath, dataType: 'json', type: 'POST', data: query, success: function (data, status) {
                populateData(data);
            }});
            function populateData(data) {
                if (!data) {
                    return;
                }
                $sel.data('result', data);
                $sel.data('options', mbOptions);

                // process response
                $sel.empty();
                
                $sel.append('<div class="path">'+mbOptions.messages['path']+': ' + data.rootPath + data.path + '</div>');
                $sel.append("<table data-tablesaw-sortable  class=\"tablesaw tablesaw-stack\" data-tablesaw-mode=\"stack\"><thead><tr><th class=\"file-name\" data-tablesaw-sortable-col data-tablesaw-sortable-default-col>"+mbOptions.messages['name']+"</th><th class=\"file-size\" data-tablesaw-sortable-col>"+mbOptions.messages['size']+"</th><th class=\"file-used\" data-tablesaw-sortable-col>"+mbOptions.messages['inused']+"</th><th class=\"file-date\" data-tablesaw-sortable-col>"+mbOptions.messages['lastmodifieddate']+"</th></tr></thead><tbody></tbody></table>");
                $sel.append('<div class="buttons"><button class="deleteMulti">'+mbOptions.messages['deletemulti']+'</button> <button class="deleteUnused">'+mbOptions.messages['deleteUnused']+'</button></div>');
                
                if (data.path !== "") {
                    drawItem($sel, {path: '..', type: 1});
                }
                if (data.files.length === 0) {
                    $sel.find("tbody").append('<tr class="msg"><td colspan="5">'+mbOptions.messages['no_file']+'</td></tr>');
                }

                $(data.files).each(function () {
                    drawItem($sel, this);
                });

                $sel.append('<div style="clear:both"></div>');

                $('.' + mbOptions.baseClass + " .clickable", $sel).bind('click', itemClick);
                $('.' + mbOptions.baseClass + " .delete", $sel).bind('click', deleteItem);
                
                $sel.find(".deleteMulti").off('click');
                $sel.find(".deleteMulti").on('click', deleteSelected);
                $sel.find(".deleteUnused").off('click');
                $sel.find(".deleteUnused").on('click', deleteUnused);
                
                try {
                    $sel.find('table').tablesaw().data( "tablesaw" ).refresh();
                } catch (err) {}
                $sel.find('button').addClass("waves-effect btn waves-button waves-float");
                
                $sel.find("[data-tablesaw-sortable-col]").data("tablesaw-sort", function( ascending ) {
                    return function( a, b ) {
                        var cellA = $(a.element).data("sortvalue"), cellB = $(b.element).data("sortvalue");
                        
                        if (cellA === "" || cellA === "..") {
                            return -1;
                        } else if (cellB === "" || cellB === "..") {
                            return 1;
                        }
                        
                        if( ascending ) {
                            return sort( cellA, cellB );
                        } else {
                            return sort( cellB, cellA );
                        }
                    };
		});
            };
            function itemClick() {
                var tr = $(this).closest("tr");
                if ($(tr).hasClass(mbOptions.baseClass)) {
                    var item = $(tr).data('item');
                    if (item.type !== ICONTYPE_FOLDER) {
                        if ((/\.(gif|jpg|jpeg|tiff|png|svg|pdf|txt)$/i).test(item.path.toLowerCase())) {
                            JPopup.show(frameId, mbOptions.ajaxPath, {"path":mbOptions.path, "preview":item.path}, "", "80%", "80%", "GET");
                        }
                    } else {
                        var path = item.path;
                        if (item.target.data('result').path) path = item.target.data('result').path + '/' + path;
                        reloadFolder(item.target,path);
                    }
                }
            };
            function refreshView(target) {
                reloadFolder(target,target.data('result').path);
            };
            function reloadFolder(target,path) {
                target.formUploadManager($.extend({},mbOptions,{path:path}),pluploadOptions);
            };
            function deleteItem() {
                var from = $(this).closest("."+mbOptions.baseClass);
                if (confirm(mbOptions.messages['delete'].replace("{filename}", from.data('item').path))) {
                    var ajaxData = {path:mbOptions.path,'delete':from.data('item').path};
                    $.ajax({url:mbOptions.ajaxPath,data:ajaxData,type:'POST',dataType:'json',success:function(data, status) {
                        populateData(data);
                    }});
                }
            };
            function deleteSelected() {
                if ($sel.find("tbody input[type='checkbox']:checked").length > 0) {
                    if (confirm(mbOptions.messages['deleteMultipleMsg'])) {
                        var list = [];
                        $sel.find("tbody input[type='checkbox']:checked").each(function(){
                            var from = $(this).closest("."+mbOptions.baseClass);
                            list.push(from.data('item').path);
                        });
                        var ajaxData = {path:mbOptions.path,'deleteMultiple':list};
                        $.ajax({url:mbOptions.ajaxPath,data:ajaxData,type:'POST',dataType:'json',success:function(data, status) {
                            populateData(data);
                        }});
                    }
                }
            };
            function deleteUnused() {
                if (confirm(mbOptions.messages['deleteUnusedMsg'])) {
                    var ajaxData = {path:mbOptions.path,'deleteUnused':true};
                    $.ajax({url:mbOptions.ajaxPath,data:ajaxData,type:'POST',dataType:'json',success:function(data, status) {
                        populateData(data);
                    }});
                }
            };
        });

        var ICONTYPE_FILE = 0;
        var ICONTYPE_FOLDER = 1;
        
        function drawItem(target, item) {
            var $table = $(target).find("tbody");
            
            item.target = $(target);
            item.fullPath = item.target.data('result').rootPath + item.target.data('result').path + '/' + item.path;
            
            var tr = $('<tr title="' + item.path + '"></tr>');
            tr.data('item', item);
            tr.addClass(mbOptions.baseClass);

            // set classes
            if (item.type === ICONTYPE_FOLDER) {
                tr.addClass(mbOptions.folderClass);
            }
            tr.data('result', target.data('result'));
            
            var size = "<td class=\"file-size\"></td>";
            var date = "<td class=\"file-date\"></td>";
            var used = "<td class=\"file-used\"></td>";
            var del = "<div class=\"nodelete\"></div>";
            var clickable = "clickable";
            if (item.path !== "..") {
                del = '<div class="delete">x</div><input class="deleteMul" type="checkbox"/> ';
                size = '<td  class="file-size" data-sortvalue="'+item.sizeByte+'"><span class="size">' + item.size + '</span></td>';
                date = '<td class="file-date" data-sortvalue="'+item.timestamp+'">'+item.date+'</td>';
                used = '<td class="file-used" data-sortvalue="'+item.used+'">'+((item.used === 1)?'<div class="tick"></div>':'')+'</td>';
            }
            
            if (item.type !== ICONTYPE_FOLDER && !(/\.(gif|jpg|jpeg|tiff|png|svg|pdf|txt)$/i).test(item.path.toLowerCase())) {
                clickable = "";
            }
            
            $table.append(tr);
            $(tr).append('<td class="file-name" data-sortvalue="'+item.path+'" >'+del+'<div class="file-label '+clickable+'">' + item.path + '</div></td>' + size + used + date);
        }

        return $(this);
    };
})(jQuery);