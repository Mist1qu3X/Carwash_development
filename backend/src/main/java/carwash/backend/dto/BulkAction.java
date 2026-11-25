package carwash.backend.dto;

// виды массовых операций над записями
public enum BulkAction {
    ARCHIVE,
    RESTORE,
    CHANGE_STATUS,
    ADD_TAG,
    REMOVE_TAG
}
