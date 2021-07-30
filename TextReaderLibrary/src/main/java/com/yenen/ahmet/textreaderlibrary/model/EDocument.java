package com.yenen.ahmet.textreaderlibrary.model;

import org.jmrtd.lds.icao.DG12File;

import java.security.PublicKey;

public class EDocument {

    private DocType docType;
    private PersonDetails personDetails;
    private AdditionalPersonDetails additionalPersonDetails;
    private PublicKey docPublicKey;
    private DG12File dg12File;

    public DocType getDocType() {
        return docType;
    }

    public void setDocType(DocType docType) {
        this.docType = docType;
    }

    public PersonDetails getPersonDetails() {
        return personDetails;
    }

    public void setPersonDetails(PersonDetails personDetails) {
        this.personDetails = personDetails;
    }

    public AdditionalPersonDetails getAdditionalPersonDetails() {
        return additionalPersonDetails;
    }

    public void setAdditionalPersonDetails(AdditionalPersonDetails additionalPersonDetails) {
        this.additionalPersonDetails = additionalPersonDetails;
    }

    public PublicKey getDocPublicKey() {
        return docPublicKey;
    }

    public void setDocPublicKey(PublicKey docPublicKey) {
        this.docPublicKey = docPublicKey;
    }

    public void setDg12File(final DG12File dg12File){
        this.dg12File = dg12File;
    }

    public DG12File getDg12File(){
        return dg12File;
    }
}
