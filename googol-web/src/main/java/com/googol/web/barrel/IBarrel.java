package com.googol.web.barrel;

import com.googol.web.common.PageData;
import com.googol.web.common.UrlMetadata;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IBarrel extends Remote {

    // Funcionalidades Principais
    void storePage(PageData page) throws RemoteException;
    Map<String, UrlMetadata> search(List<String> terms) throws RemoteException;

    // Consultas de Links
    boolean isUrlInBarrel(String url) throws RemoteException;
    Set<String> getIncomingLinks(String url) throws RemoteException;

    // Sincronização de Dados
    Map<String, Set<String>> getInvertedIndex() throws RemoteException;
    Map<String, Set<String>> getIncomingLinksMap() throws RemoteException;
    Map<String, UrlMetadata> getPageMetadata() throws RemoteException;

    // Gestão e Estado
    String getName() throws RemoteException;
    boolean isActive() throws RemoteException;
    int getIndexSize() throws RemoteException;
    String getSystemStats() throws RemoteException;
}