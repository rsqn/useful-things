package tech.rsqn.cacheservice.testmodel;

import java.io.Serializable;

import java.util.List;

public class TestDepartmentEntity implements Serializable {
    private String name;
    private List<TestAssetEntity> assets;
    private List<TestUserEntity> users;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TestAssetEntity> getAssets() {
        return assets;
    }

    public void setAssets(List<TestAssetEntity> assets) {
        this.assets = assets;
    }

    public List<TestUserEntity> getUsers() {
        return users;
    }

    public void setUsers(List<TestUserEntity> users) {
        this.users = users;
    }
}
