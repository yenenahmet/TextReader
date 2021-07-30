package com.yenen.ahmet.textreaderlibrary.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.yenen.ahmet.textreaderlibrary.model.AdditionalPersonDetails;
import com.yenen.ahmet.textreaderlibrary.model.DocType;
import com.yenen.ahmet.textreaderlibrary.model.EDocument;
import com.yenen.ahmet.textreaderlibrary.model.PersonDetails;
import com.yenen.ahmet.textreaderlibrary.model.ReadOcrStep;

import net.sf.scuba.smartcards.CardFileInputStream;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;

import org.jmrtd.BACKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardSecurityFile;
import org.jmrtd.lds.DisplayedImageInfo;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG11File;
import org.jmrtd.lds.icao.DG12File;
import org.jmrtd.lds.icao.DG15File;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.icao.DG3File;
import org.jmrtd.lds.icao.DG5File;
import org.jmrtd.lds.icao.DG7File;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
import org.jmrtd.lds.iso19794.FingerImageInfo;
import org.jmrtd.lds.iso19794.FingerInfo;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jmrtd.PassportService.DEFAULT_MAX_BLOCKSIZE;
import static org.jmrtd.PassportService.NORMAL_MAX_TRANCEIVE_LENGTH;

public class ReadOcr {

    private final static String TAG = ReadOcr.class.getSimpleName();
    private final Context context;
    private final static int timeOut = 10000;
    private final IsoDep isoDep;
    private final BACKeySpec bacKey;
    private final ReadOcrListener listener;

    public ReadOcr(Context context, IsoDep isoDep, BACKeySpec bacKey, ReadOcrListener listener) {
        this.isoDep = isoDep;
        this.bacKey = bacKey;
        this.listener = listener;
        this.context = context;
    }

    public void startToRead() {
        final EDocument eDocument = new EDocument();
        final PersonDetails personDetails = new PersonDetails();
        final AdditionalPersonDetails additionalPersonDetails = new AdditionalPersonDetails();
        final Thread thread = new Thread(() -> {
            try {
                listener.onLoading(ReadOcrStep.START_TO_READING);
                final CardService cardService = CardService.getInstance(isoDep);
                cardService.open();

                final PassportService service = new PassportService(cardService, NORMAL_MAX_TRANCEIVE_LENGTH, DEFAULT_MAX_BLOCKSIZE, true, false);
                sendSelectApplet(service);
                setPersonalDetails(service, personDetails,eDocument);
                setFaceImage(service, personDetails);
                setFingerprint(service, personDetails);
                setPortraitPicture(service, personDetails);
                setSignature(service, personDetails);
                setAdditionalDetails(service, additionalPersonDetails);
                setDg12(service, eDocument);
                setDocumentPublic(service, eDocument);

                eDocument.setPersonDetails(personDetails);
                eDocument.setAdditionalPersonDetails(additionalPersonDetails);
                listener.onSuccess(eDocument);

                cardService.close();
            } catch (Exception e) {
                listener.onFail(e);
            }
        });
        thread.start();
    }

    private void sendSelectApplet(final PassportService service) throws CardServiceException {
        service.open();
        isoDep.setTimeout(timeOut);
        boolean paceSucceeded = false;
        try {
            final CardSecurityFile cardSecurityFile = new CardSecurityFile(service.getInputStream(PassportService.EF_CARD_SECURITY));
            final Collection<SecurityInfo> securityInfoCollection = cardSecurityFile.getSecurityInfos();
            for (final SecurityInfo securityInfo : securityInfoCollection) {
                if (securityInfo instanceof PACEInfo) {
                    final PACEInfo paceInfo = (PACEInfo) securityInfo;
                    service.doPACE(bacKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()), null);
                    paceSucceeded = true;
                }
            }
        } catch (Exception e) {
            Log.w("sendSelectApplet", e);
        }

        service.sendSelectApplet(paceSucceeded);

        if (!paceSucceeded) {
            try {
                service.getInputStream(PassportService.EF_COM).read();
            } catch (Exception e) {
                service.doBAC(bacKey);
            }
        }
    }

    private void setPersonalDetails(final PassportService service,
                                    final PersonDetails personDetails,
                                    final EDocument eDocument)
            throws CardServiceException, IOException {
        listener.onLoading(ReadOcrStep.ON_DG1);
        final CardFileInputStream dg1In = service.getInputStream(PassportService.EF_DG1);
        final DG1File dg1File = new DG1File(dg1In);

        final MRZInfo mrzInfo = dg1File.getMRZInfo();
        personDetails.setName(mrzInfo.getSecondaryIdentifier().replace("<", " ").trim());
        personDetails.setSurname(mrzInfo.getPrimaryIdentifier().replace("<", " ").trim());
        personDetails.setPersonalNumber(mrzInfo.getPersonalNumber());
        personDetails.setGender(mrzInfo.getGender().toString());
        personDetails.setBirthDate(DateUtil.convertFromMrzDate(mrzInfo.getDateOfBirth()));
        personDetails.setExpiryDate(DateUtil.convertFromMrzDate(mrzInfo.getDateOfExpiry()));
        personDetails.setSerialNumber(mrzInfo.getDocumentNumber());
        personDetails.setNationality(mrzInfo.getNationality());
        personDetails.setIssuerAuthority(mrzInfo.getOptionalData2());

        if ("I".equals(mrzInfo.getDocumentCode())) {
            eDocument.setDocType(DocType.ID_CARD);
        } else if ("P".equals(mrzInfo.getDocumentCode())) {
            eDocument.setDocType(DocType.PASSPORT);
        }
    }

    private void setFaceImage(final PassportService service, final PersonDetails personDetails) throws CardServiceException, IOException {
        listener.onLoading(ReadOcrStep.ON_DG2);
        final CardFileInputStream dg2In = service.getInputStream(PassportService.EF_DG2);
        final DG2File dg2File = new DG2File(dg2In);

        final List<FaceInfo> faceInfos = dg2File.getFaceInfos();
        final List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
        for (final FaceInfo faceInfo : faceInfos) {
            allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
        }

        if (!allFaceImageInfos.isEmpty()) {
            final FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();
            final Image image = ImageUtil.getImage(context, faceImageInfo);
            personDetails.setFaceImage(image.getBitmapImage());
            personDetails.setFaceImageBase64(image.getBase64Image());
        }
    }

    private void setFingerprint(final PassportService service, final PersonDetails personDetails) {
        try {
            listener.onLoading(ReadOcrStep.ON_DG3);
            final CardFileInputStream dg3In = service.getInputStream(PassportService.EF_DG3);
            final DG3File dg3File = new DG3File(dg3In);

            final List<FingerInfo> fingerInfos = dg3File.getFingerInfos();
            final List<FingerImageInfo> allFingerImageInfos = new ArrayList<>();
            for (final FingerInfo fingerInfo : fingerInfos) {
                allFingerImageInfos.addAll(fingerInfo.getFingerImageInfos());
            }
            final List<Bitmap> fingerprintsImage = new ArrayList<>();
            if (!allFingerImageInfos.isEmpty()) {
                for (final FingerImageInfo fingerImageInfo : allFingerImageInfos) {
                    final Image image = ImageUtil.getImage(context, fingerImageInfo);
                    fingerprintsImage.add(image.getBitmapImage());
                }
                personDetails.setFingerprints(fingerprintsImage);
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    private void setPortraitPicture(final PassportService service, final PersonDetails personDetails) {
        try {
            listener.onLoading(ReadOcrStep.ON_DG5);
            final CardFileInputStream dg5In = service.getInputStream(PassportService.EF_DG5);
            final DG5File dg5File = new DG5File(dg5In);

            final List<DisplayedImageInfo> displayedImageInfos = dg5File.getImages();
            if (!displayedImageInfos.isEmpty()) {
                final DisplayedImageInfo displayedImageInfo = displayedImageInfos.iterator().next();
                final Image image = ImageUtil.getImage(context, displayedImageInfo);
                personDetails.setPortraitImage(image.getBitmapImage());
                personDetails.setPortraitImageBase64(image.getBase64Image());
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    private void setSignature(final PassportService service, final PersonDetails personDetails) {
        try {
            listener.onLoading(ReadOcrStep.ON_DG7);
            final CardFileInputStream dg7In = service.getInputStream(PassportService.EF_DG7);
            final DG7File dg7File = new DG7File(dg7In);

            final List<DisplayedImageInfo> signatureImageInfos = dg7File.getImages();
            if (!signatureImageInfos.isEmpty()) {
                final DisplayedImageInfo displayedImageInfo = signatureImageInfos.iterator().next();
                final Image image = ImageUtil.getImage(context, displayedImageInfo);
                personDetails.setPortraitImage(image.getBitmapImage());
                personDetails.setPortraitImageBase64(image.getBase64Image());
            }

        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    private void setAdditionalDetails(final PassportService service, final AdditionalPersonDetails additionalPersonDetails) {
        try {
            listener.onLoading(ReadOcrStep.ON_DG11);
            final CardFileInputStream dg11In = service.getInputStream(PassportService.EF_DG11);
            final DG11File dg11File = new DG11File(dg11In);
            if (dg11File.getLength() > 0) {
                additionalPersonDetails.setCustodyInformation(dg11File.getCustodyInformation());
                additionalPersonDetails.setNameOfHolder(dg11File.getNameOfHolder());
                additionalPersonDetails.setFullDateOfBirth(dg11File.getFullDateOfBirth());
                additionalPersonDetails.setOtherNames(dg11File.getOtherNames());
                additionalPersonDetails.setOtherValidTDNumbers(dg11File.getOtherValidTDNumbers());
                additionalPersonDetails.setPermanentAddress(dg11File.getPermanentAddress());
                additionalPersonDetails.setPersonalNumber(dg11File.getPersonalNumber());
                additionalPersonDetails.setPersonalSummary(dg11File.getPersonalSummary());
                additionalPersonDetails.setPlaceOfBirth(dg11File.getPlaceOfBirth());
                additionalPersonDetails.setProfession(dg11File.getProfession());
                additionalPersonDetails.setProofOfCitizenship(dg11File.getProofOfCitizenship());
                additionalPersonDetails.setTag(dg11File.getTag());
                additionalPersonDetails.setTagPresenceList(dg11File.getTagPresenceList());
                additionalPersonDetails.setTelephone(dg11File.getTelephone());
                additionalPersonDetails.setTitle(dg11File.getTitle());
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    private void setDg12(final PassportService service, final EDocument eDocument) {
        try {
            listener.onLoading(ReadOcrStep.ON_DG12);
            final CardFileInputStream dg11In = service.getInputStream(PassportService.EF_DG12);
            final DG12File dg11File = new DG12File(dg11In);

            if (dg11File.getLength() > 0) {
                eDocument.setDg12File(dg11File);
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    private void setDocumentPublic(final PassportService service, final EDocument eDocument) {
        try {
            listener.onLoading(ReadOcrStep.ON_DG15);
            final CardFileInputStream dg15In = service.getInputStream(PassportService.EF_DG15);
            final DG15File dg15File = new DG15File(dg15In);
            final PublicKey publicKey = dg15File.getPublicKey();
            eDocument.setDocPublicKey(publicKey);
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

}
