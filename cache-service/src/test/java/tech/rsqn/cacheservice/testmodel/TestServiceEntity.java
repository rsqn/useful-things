/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.testmodel;

import java.io.Serializable;


/**
 * Author: mandrewes
 * Date: 16/06/11
 *
 *
 * @author mandrewes
 */
public class TestServiceEntity implements Serializable {
    private int id;
    private String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
