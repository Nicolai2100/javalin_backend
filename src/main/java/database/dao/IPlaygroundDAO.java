package database.dao;

import com.mongodb.WriteResult;
import database.DALException;
import database.collections.Playground;

import java.util.List;

public interface IPlaygroundDAO {
    String COLLECTION = "playgrounds";

    WriteResult createPlayground(Playground playground) throws DALException;

    Playground getPlayground(String id) throws DALException;

    List<Playground> getPlaygroundList() throws DALException;

    boolean updatePlayground(Playground playground) throws DALException;

    boolean deletePlayground(String id) throws DALException;

    boolean deleteAllPlaygrounds() throws DALException;
}
