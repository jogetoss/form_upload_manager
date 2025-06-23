package org.joget.plugin.userview;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.SetupManager;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class FormUploadManager extends UserviewMenu implements PluginWebSupport {
    public final String MESSAGE_PATH = "message/FormUploadManager";

    @Override
    public String getCategory() {
        return "Custom";
    }

    @Override
    public String getIcon() {
        return "/plugin/org.joget.apps.userview.lib.HtmlPage/images/grid_icon.gif";
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    public String getName() {
        return "Form Upload Manager";
    }

    public String getVersion() {
        return "0.0.0";
    }

    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.plugin.userview.FormUploadManager.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.plugin.userview.FormUploadManager.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/userview/formUploadManager.json", null, true, MESSAGE_PATH);
    }
    
    @Override
    public String getRenderPage() {
        if (!"true".equals(getPropertyString("allowNonAdmin")) && !WorkflowUtil.isCurrentUserInRole(WorkflowUtil.ROLE_ADMIN)) {
            return "<p>"+ResourceBundleUtil.getMessage("general.content.unauthorized")+"</p>";
        }
        
        Map model = new HashMap();
        model.put("request", getRequestParameters());
        model.put("element", this);
        
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String userviewId = getUserview().getPropertyString("id");
        String key = (getKey() != null)? getKey() : "";
        String menuId = getPropertyString("customId");
        if (menuId == null || menuId.trim().isEmpty()) {
            menuId = getPropertyString("id");
        }
        String menuKey = userviewId + ":" + key + ":" + menuId;
        String allowedDirs = "app_formuploads/" + getPropertyString("allowedDirs");
        
        String nonce = SecurityUtil.generateNonce(new String[]{"FormUploadManager", appDef.getId(), appDef.getVersion().toString(), menuKey, allowedDirs}, 4);
        try {
            nonce = URLEncoder.encode(nonce, "UTF-8");
        } catch (Exception e) {}
        model.put("nonce", nonce);
        
        try {
            allowedDirs = URLEncoder.encode(SecurityUtil.encrypt(allowedDirs), "UTF-8");
            menuKey = URLEncoder.encode(SecurityUtil.encrypt(menuKey), "UTF-8");
        } catch (Exception e) {}
        
        String jsonUrl = "/web/json/app/"+appDef.getId()+"/"+appDef.getVersion()+"/plugin/org.joget.plugin.userview.FormUploadManager/service?_k1=" + menuKey + "&_k2=" + allowedDirs + "&_nonce=" + nonce;
        model.put("jsonUrl", jsonUrl);
        
        PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
        String content = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/formUploadManager.ftl", MESSAGE_PATH);
        return content;
    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) || request.getParameter("preview") != null) {
            String menuKey = request.getParameter("_k1");
            String allowedDirs = request.getParameter("_k2");
            String nonce = request.getParameter("_nonce");

            try {
                menuKey = SecurityUtil.decrypt(menuKey);
                allowedDirs = SecurityUtil.decrypt(allowedDirs);

                AppDefinition appDef = AppUtil.getCurrentAppDefinition();

                if (!SecurityUtil.verifyNonce(nonce, new String[]{"FormUploadManager", appDef.getAppId(), appDef.getVersion().toString(), menuKey, allowedDirs})) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                allowedDirs = allowedDirs.replace("app_formuploads/", "");
                List<String> dirs = new ArrayList<String>();
                if (!allowedDirs.isEmpty()) {
                    dirs.addAll(Arrays.asList(allowedDirs.split(";")));
                }
                
                String path = resolvePath(request.getParameter("path"));
                boolean isValidPath = true;
                
                // validate path
                String normalizedPath = Normalizer.normalize(path, Normalizer.Form.NFKC);
                if (normalizedPath.startsWith("..") || normalizedPath.contains("../") || normalizedPath.contains("..\\")) {
                    path = "";
                    isValidPath = false;
                }
                
                String rootPath = "app_formuploads/";
                
                //check path is in allowed dirs
                if (!dirs.isEmpty() && !path.isEmpty()) {
                    String tableDir = path;
                    if (path.indexOf("/") > 0) {
                        tableDir = path.substring(0, path.indexOf("/"));
                    }
                    if (!dirs.contains(tableDir)) {
                        path = "";
                        isValidPath = false;
                    }
                }
                
                if (request.getParameter("preview") != null) {
                    boolean noContent = true;
                    
                    if (isValidPath) {
                        String preview = request.getParameter("preview");
                        String normalizedPreviewPath = Normalizer.normalize(preview, Normalizer.Form.NFKC);
                        if (!(normalizedPreviewPath.startsWith("..") || normalizedPreviewPath.contains("../") || normalizedPreviewPath.contains("..\\"))) {
                            File previewPathDir = new File(SetupManager.getBaseDirectory() + File.separator + rootPath + path + File.separator + preview);
                            if (previewPathDir.exists()) {
                                ServletOutputStream stream = response.getOutputStream();
                                DataInputStream in = new DataInputStream(new FileInputStream(previewPathDir));
                                byte[] bbuf = new byte[65536];

                                try {
                                    String contentType = request.getSession().getServletContext().getMimeType(previewPathDir.getName());
                                    if (contentType != null) {
                                        response.setContentType(contentType);
                                    }

                                    // send output
                                    int length = 0;
                                    while ((in != null) && ((length = in.read(bbuf)) != -1)) {
                                        stream.write(bbuf, 0, length);
                                    }
                                } finally {
                                    in.close();
                                    stream.flush();
                                    stream.close();
                                }
                            }
                        }
                    }
                    
                    if (noContent) {
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                    return;
                }

                if (isValidPath && request.getParameter("delete") != null) {
                    String delete = request.getParameter("delete");
                    String normalizedDeletePath = Normalizer.normalize(delete, Normalizer.Form.NFKC);
                    if (!(normalizedDeletePath.startsWith("..") || normalizedDeletePath.contains("../") || normalizedDeletePath.contains("..\\"))) {
                        File deletPathDir = new File(SetupManager.getBaseDirectory() + File.separator + rootPath + path + File.separator + delete);
                        if (deletPathDir.exists()) {
                            if (deletPathDir.isDirectory()) {
                                FileUtils.deleteDirectory(deletPathDir);
                            } else {
                                deletPathDir.delete();
                            }
                        }
                    }
                } else if (isValidPath && request.getParameterValues("deleteMultiple[]") != null) {
                    String[] deletelist = request.getParameterValues("deleteMultiple[]");
                    for (String delete : deletelist) {
                        String normalizedDeletePath = Normalizer.normalize(delete, Normalizer.Form.NFKC);
                        if (!(normalizedDeletePath.startsWith("..") || normalizedDeletePath.contains("../") || normalizedDeletePath.contains("..\\"))) {
                            File deletPathDir = new File(SetupManager.getBaseDirectory() + File.separator + rootPath + path + File.separator + delete);
                            if (deletPathDir.exists()) {
                                if (deletPathDir.isDirectory()) {
                                    FileUtils.deleteDirectory(deletPathDir);
                                } else {
                                    deletPathDir.delete();
                                }
                            }
                        }
                    }
                }
                
                
                boolean isRoot = false;
                File rootDir = new File(SetupManager.getBaseDirectory() + File.separator + rootPath);
                File pathDir = new File(SetupManager.getBaseDirectory() + File.separator + rootPath + File.separator + path);
                
                if (rootDir.equals(pathDir)) {
                    isRoot = true;
                }
                
                JSONObject result = new JSONObject();
                result.put("rootPath", rootPath);
                result.put("path", path);
                
                Set<String> usages = getUsages(path);
                
                JSONArray filesArray = new JSONArray();
                File[] files = pathDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if ((!isRoot || (isRoot && dirs.isEmpty()) || (isRoot && dirs.contains(f.getName()))) && !f.getName().startsWith(".")) {
                            JSONObject fObj = new JSONObject();
                            
                            boolean usage = false;
                            if (usages.contains(f.getName())) {
                                usage = true;
                            } else if (f.getName().endsWith(FileManager.THUMBNAIL_EXT)) {
                                String temp = f.getName().replace(FileManager.THUMBNAIL_EXT, "");
                                if (usages.contains(temp)) {
                                    usage = true;
                                }
                            }
                            
                            if (isValidPath && request.getParameter("deleteUnused") != null && !usage) {
                                if (f.exists()) {
                                    if (f.isDirectory()) {
                                        FileUtils.deleteDirectory(f);
                                    } else {
                                        f.delete();
                                    }
                                }
                            } else {
                                fObj.put("path", f.getName());
                                fObj.put("type", f.isDirectory()?1:0);
                                long size = f.isDirectory()?FileUtils.sizeOfDirectory(f):f.length();
                                fObj.put("sizeByte", size);
                                fObj.put("size", fileSize(size));
                                fObj.put("date", TimeZoneUtil.convertToTimeZone(new Date(f.lastModified()), null, AppUtil.getAppDateFormat()));
                                fObj.put("timestamp", f.lastModified());
                            
                                fObj.put("used", usage?1:0);
                                filesArray.put(fObj);
                            }
                        }
                    }
                }
                
                result.put("files", filesArray);
                
                result.write(response.getWriter());
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
    
    private Set<String> getUsages(String path) {
        Set<String> usages = new HashSet<String>();
        
        try {
            if (path.isEmpty()) {
                AppDefinitionDao appDefDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
                Collection<AppDefinition> appDefinitionList = appDefDao.findLatestVersions(null, null, null, null, null, null, null);
                if (appDefinitionList != null && !appDefinitionList.isEmpty()) {
                    for (AppDefinition apps : appDefinitionList) {
                        FormDefinitionDao dao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
                        Collection<String> tableNameList = dao.getTableNameList(apps);
                        if (tableNameList != null && !tableNameList.isEmpty()) {
                            for (String name : tableNameList) {
                                usages.add(name);
                            }
                        }
                    }
                }
            } else {
                FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                String[] temp = path.split("/");
                if (temp.length == 1) {
                    String tablename = temp[0];
                    List<Map<String, Object>> list = dao.findCustomQuery(tablename, tablename, new String[]{"id"}, new String[]{"id"}, null, null, null, null, null, null, null, null, null, null);
                    if (list != null && !list.isEmpty()) {
                        for (Map<String, Object> m : list) {
                            usages.add(m.get("id").toString());
                        }
                    }
                } else {
                    String tablename = temp[0];
                    String primaryKey = temp[1];

                    FormRow row = dao.load(tablename, tablename, primaryKey);
                    if (row != null) {
                        for (Object key : row.getCustomProperties().keySet()) {
                            String[] values = row.getCustomProperties().get(key).toString().split(";");
                            usages.addAll(Arrays.asList(values));
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        return usages;
    }
    
    private String resolvePath(String path) {
        if (path.endsWith("/..")) {
            path = path.replace("/..", "");
            if (path.lastIndexOf("/") > 0) {
                path = path.substring(0, path.lastIndexOf("/"));
            } else {
                path = "";
            }
        }
        return path;
    }
    
    private String fileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
