package tech.rsqn.cacheservice.testmodel;

import java.io.Serializable;
import java.util.List;

public class TestUserEntity implements Serializable {
    private String name;
    private int id;
    private TestServiceEntity primaryService;
    private List<TestServiceEntity> otherServices;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public TestServiceEntity getPrimaryService() {
        return primaryService;
    }

    public void setPrimaryService(TestServiceEntity primaryService) {
        this.primaryService = primaryService;
    }

    public List<TestServiceEntity> getOtherServices() {
        return otherServices;
    }

    public void setOtherServices(List<TestServiceEntity> otherServices) {
        this.otherServices = otherServices;
    }
}
