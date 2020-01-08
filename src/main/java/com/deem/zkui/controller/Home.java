/**
 *
 * Copyright (c) 2014, Deem Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.deem.zkui.controller;

import com.deem.zkui.dao.Dao;
import com.deem.zkui.utils.ServletUtil;
import com.deem.zkui.utils.ZooKeeperUtil;
import com.deem.zkui.vo.LeafBean;
import com.deem.zkui.vo.ZKNode;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = {"/home"}, loadOnStartup = 1)
public class Home extends HttpServlet {

    private final static Logger logger = LoggerFactory.getLogger(Home.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.debug("Home Get Action!");
        try {
            Properties globalProps = (Properties) this.getServletContext().getAttribute("globalProps");
            String zkServer = globalProps.getProperty("zkServer");
            String[] zkServerLst = zkServer.split(",");

            Map<String, Object> templateParam = new HashMap<>();
            String zkPath = request.getParameter("zkPath");
            String navigate = request.getParameter("navigate");
            ZooKeeper zk = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);
            List<String> nodeLst;
            List<LeafBean> leafLst;
            String currentPath, parentPath, displayPath;
            String authRole = (String) request.getSession().getAttribute("authRole");
            if (authRole == null) {
                authRole = ZooKeeperUtil.ROLE_USER;
            }

            if (zkPath == null || zkPath.equals("/")) {
                templateParam.put("zkpath", "/");
                ZKNode zkNode = ZooKeeperUtil.INSTANCE.listNodeEntries(zk, "/", authRole);
                nodeLst = zkNode.getNodeLst();
                leafLst = zkNode.getLeafBeanLSt();
                currentPath = "/";
                displayPath = "/";
                parentPath = "/";
            } else {
                templateParam.put("zkPath", zkPath);
                ZKNode zkNode = ZooKeeperUtil.INSTANCE.listNodeEntries(zk, zkPath, authRole);
                nodeLst = zkNode.getNodeLst();
                leafLst = zkNode.getLeafBeanLSt();
                currentPath = zkPath + "/";
                displayPath = zkPath;
                parentPath = zkPath.substring(0, zkPath.lastIndexOf("/"));
                if (parentPath.equals("")) {
                    parentPath = "/";
                }
            }

            templateParam.put("displayPath", displayPath);
            templateParam.put("parentPath", parentPath);
            templateParam.put("currentPath", currentPath);
            templateParam.put("nodeLst", nodeLst);
            templateParam.put("leafLst", leafLst);
            templateParam.put("breadCrumbLst", displayPath.split("/"));
            templateParam.put("scmRepo", globalProps.getProperty("scmRepo"));
            templateParam.put("scmRepoPath", globalProps.getProperty("scmRepoPath"));
            templateParam.put("navigate", navigate);

            ServletUtil.INSTANCE.renderHtml(request, response, templateParam, "home.ftl.html");

        } catch (KeeperException | InterruptedException | TemplateException ex) {
            logger.error(Arrays.toString(ex.getStackTrace()));
            ServletUtil.INSTANCE.renderError(request, response, ex.getMessage());
        }

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.debug("Home Post Action!");
        try {
            // 获取全局配置
            Properties globalProps = (Properties) this.getServletContext().getAttribute("globalProps");
            Dao dao = new Dao(globalProps);

            // 获取zk服务器
            String zkServer = globalProps.getProperty("zkServer");
            String[] zkServerLst = zkServer.split(",");


            // 获取请求参数
            Map<String, Object> templateParam = new HashMap<>();
            String currentPath = request.getParameter("currentPath");
            String displayPath = request.getParameter("displayPath");
            String newProperty = request.getParameter("newProperty");
            String newValue = request.getParameter("newValue");
            String newNode = request.getParameter("newNode");
            String[] nodeChkGroup = request.getParameterValues("nodeChkGroup");
            String[] propChkGroup = request.getParameterValues("propChkGroup");
            String searchStr = request.getParameter("searchStr").trim();


            String action = request.getParameter("action");
            String authRole = (String) request.getSession().getAttribute("authRole");
            switch (action) {
                case "Save Node":
                    saveNode(request, response, globalProps, dao, zkServerLst, newNode, currentPath, authRole, displayPath);
                    break;
                case "Save Property":
                    saveProperty(request, response, globalProps, dao, zkServerLst, newProperty, newValue, currentPath, authRole, displayPath);
                    break;
                case "Update Property":
                    updateProperty(request, response, globalProps, dao, zkServerLst, newProperty, newValue, currentPath, authRole, displayPath);
                    break;
                case "Search":
                    search(request, response, globalProps, dao, zkServerLst, templateParam, searchStr, authRole);
                    break;
                case "Delete":
                    delete(request, response, globalProps, dao, zkServerLst, nodeChkGroup, propChkGroup, authRole, displayPath);
                    break;
                default:
                    response.sendRedirect("/home");
            }

        } catch (InterruptedException | TemplateException | KeeperException ex) {
            logger.error(Arrays.toString(ex.getStackTrace()));
            ServletUtil.INSTANCE.renderError(request, response, ex.getMessage());
        }
    }

    /**
     * 保存节点
     *
     * @param request
     * @param response
     * @param globalProps
     * @param dao
     * @param zkServerLst
     * @param newNode
     * @param currentPath
     * @param authRole
     * @param displayPath
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException
     */
    private void saveNode(HttpServletRequest request, HttpServletResponse response, Properties globalProps, Dao dao, String[] zkServerLst, String newNode, String currentPath, String authRole, String displayPath) throws KeeperException, InterruptedException, IOException {
        if (!newNode.equals("") && !currentPath.equals("") && authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {
            ZooKeeper zookeeper = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);

            ZooKeeperUtil.INSTANCE.createFolder(currentPath + newNode, "foo", "bar", zookeeper);
            request.getSession().setAttribute("flashMsg", "Node created!");

            String user = (String) request.getSession().getAttribute("authName");
            dao.insertHistory(user, request.getRemoteAddr(), "Creating node: " + currentPath + newNode);
        }
        response.sendRedirect("/home?zkPath=" + displayPath);
    }

    /**
     * 保存属性
     *
     * @param request
     * @param response
     * @param globalProps
     * @param dao
     * @param zkServerLst
     * @param newProperty
     * @param newValue
     * @param currentPath
     * @param authRole
     * @param displayPath
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException
     */
    private void saveProperty(HttpServletRequest request, HttpServletResponse response, Properties globalProps, Dao dao, String[] zkServerLst, String newProperty, String newValue, String currentPath, String authRole, String displayPath) throws KeeperException, InterruptedException, IOException {
        if (!newProperty.equals("") && !currentPath.equals("") && authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {

            ZooKeeper zookeeper = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);

            ZooKeeperUtil.INSTANCE.createNode(currentPath, newProperty, newValue, zookeeper);
            request.getSession().setAttribute("flashMsg", "Property Saved!");
            if (ZooKeeperUtil.INSTANCE.checkIfPwdField(newProperty)) {
                newValue = ZooKeeperUtil.INSTANCE.SOPA_PIPA;
            }

            String user = (String) request.getSession().getAttribute("authName");
            dao.insertHistory(user, request.getRemoteAddr(), "Saving Property: " + currentPath + "," + newProperty + "=" + newValue);
        }
        response.sendRedirect("/home?zkPath=" + displayPath);
    }

    /**
     * 更新属性
     *
     * @param request
     * @param response
     * @param globalProps
     * @param dao
     * @param zkServerLst
     * @param newProperty
     * @param newValue
     * @param currentPath
     * @param authRole
     * @param displayPath
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException
     */
    private void updateProperty(HttpServletRequest request, HttpServletResponse response, Properties globalProps, Dao dao, String[] zkServerLst, String newProperty, String newValue, String currentPath, String authRole, String displayPath) throws KeeperException, InterruptedException, IOException {
        if (!newProperty.equals("") && !currentPath.equals("") && authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {
            ZooKeeper zookeeper = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);
            ZooKeeperUtil.INSTANCE.setPropertyValue(currentPath, newProperty, newValue, zookeeper);
            request.getSession().setAttribute("flashMsg", "Property Updated!");
            if (ZooKeeperUtil.INSTANCE.checkIfPwdField(newProperty)) {
                newValue = ZooKeeperUtil.INSTANCE.SOPA_PIPA;
            }

            String user = (String) request.getSession().getAttribute("authName");
            dao.insertHistory(user, request.getRemoteAddr(), "Updating Property: " + currentPath + "," + newProperty + "=" + newValue);
        }
        response.sendRedirect("/home?zkPath=" + displayPath);
    }

    /**
     * 搜索
     *
     * @param request
     * @param response
     * @param globalProps
     * @param dao
     * @param zkServerLst
     * @param templateParam
     * @param searchStr
     * @param authRole
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException
     * @throws TemplateException
     */
    private void search(HttpServletRequest request, HttpServletResponse response, Properties globalProps, Dao dao, String[] zkServerLst, Map<String, Object> templateParam, String searchStr, String authRole) throws KeeperException, InterruptedException, IOException, TemplateException {
        ZooKeeper zookeeper = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);
        Set<LeafBean> searchResult = ZooKeeperUtil.INSTANCE.searchTree(searchStr, zookeeper, authRole);
        templateParam.put("searchResult", searchResult);
        ServletUtil.INSTANCE.renderHtml(request, response, templateParam, "search.ftl.html");
    }

    /**
     * 删除
     *
     * @param request
     * @param response
     * @param globalProps
     * @param dao
     * @param zkServerLst
     * @param nodeChkGroup
     * @param propChkGroup
     * @param authRole
     * @param displayPath
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException
     */
    private void delete(HttpServletRequest request, HttpServletResponse response, Properties globalProps, Dao dao, String[] zkServerLst, String[] nodeChkGroup, String[] propChkGroup, String authRole, String displayPath) throws KeeperException, InterruptedException, IOException {
        if (authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {

            String user = (String) request.getSession().getAttribute("authName");

            if (propChkGroup != null) {
                for (String prop : propChkGroup) {
                    List delPropLst = Arrays.asList(prop);
                    ZooKeeper zookeeper = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);
                    ZooKeeperUtil.INSTANCE.deleteLeaves(delPropLst, zookeeper);
                    request.getSession().setAttribute("flashMsg", "Delete Completed!");

                    dao.insertHistory(user, request.getRemoteAddr(), "Deleting Property: " + delPropLst.toString());
                }
            }
            if (nodeChkGroup != null) {
                for (String node : nodeChkGroup) {
                    List delNodeLst = Arrays.asList(node);
                    ZooKeeper zookeeper = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0], globalProps);
                    ZooKeeperUtil.INSTANCE.deleteFolders(delNodeLst, zookeeper);
                    request.getSession().setAttribute("flashMsg", "Delete Completed!");

                    dao.insertHistory(user, request.getRemoteAddr(), "Deleting Nodes: " + delNodeLst.toString());
                }
            }

        }
        response.sendRedirect("/home?zkPath=" + displayPath);
    }
}
