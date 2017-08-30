package tech.rsqn.useful.things.application.concurrency;

public class MasterSlaveSelectorUnitTestSupport implements MasterSlaveSelector{
    private boolean master = false;

    public MasterSlaveSelectorUnitTestSupport() {
    }


    public void setMaster(boolean master) {
        this.master = master;
    }

    public boolean isMaster() {
        return master;
    }
}
