<script language="javascript" type="text/javascript" src="${request.contextPath}/plugin/org.joget.plugin.userview.FormUploadManager/js/tablesaw.jquery.js"></script>
<script language="javascript" type="text/javascript" src="${request.contextPath}/plugin/org.joget.plugin.userview.FormUploadManager/js/formUploadManager.js"></script>
<link rel="stylesheet" type="text/css" href="${request.contextPath}/plugin/org.joget.plugin.userview.FormUploadManager/js/tablesaw.css" />
<link rel="stylesheet" type="text/css" href="${request.contextPath}/plugin/org.joget.plugin.userview.FormUploadManager/css/formUploadManager.css" />
<div class="formUploadManager-body-content">
    ${element.properties.customHeader!}

    <div id="formUploadManager"></div>
    <script>
        $('#formUploadManager').formUploadManager({
            ajaxPath:'${request.contextPath}${jsonUrl}',
            messages : {
                'path' : '@@userview.formUploadManager.label.path@@',
                'no_file' : '@@userview.formUploadManager.label.no_file@@',
                'delete' : '@@userview.formUploadManager.label.delete@@',
                'name' : '@@userview.formUploadManager.label.name@@',
                'size' : '@@userview.formUploadManager.label.size@@',
                'inused' : '@@userview.formUploadManager.label.inUsed@@',
                'lastmodifieddate' : '@@userview.formUploadManager.label.lastmodifieddate@@',
                'deletemulti' : '@@userview.formUploadManager.label.deletemulti@@',
                'deleteMultipleMsg' : '@@userview.formUploadManager.label.deleteMultipleMsg@@',
                'deleteUnused' : '@@userview.formUploadManager.label.deleteUnused@@',
                'deleteUnusedMsg' : '@@userview.formUploadManager.label.deleteUnusedMsg@@'
            }
        });
    </script>

    ${element.properties.customFooter!}
</div>