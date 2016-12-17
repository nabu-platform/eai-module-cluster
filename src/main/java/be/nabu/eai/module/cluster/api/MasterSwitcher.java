package be.nabu.eai.module.cluster.api;

public interface MasterSwitcher {
	public void switchMaster(String master, boolean amMaster);
}
