-- Apr 2, 2014 11:07:26 AM VET
-- LVE Withholding
CREATE TABLE LVE_CashTax (AD_Client_ID NUMBER(10) NOT NULL, AD_Org_ID NUMBER(10) NOT NULL, C_Cash_ID NUMBER(10) NOT NULL, Created DATE NOT NULL, CreatedBy NUMBER(10) NOT NULL, C_Tax_ID NUMBER(10) NOT NULL, IsActive CHAR(1) DEFAULT 'Y' CHECK (IsActive IN ('Y','N')) NOT NULL, IsTaxIncluded CHAR(1) CHECK (IsTaxIncluded IN ('Y','N')) NOT NULL, Processed CHAR(1) CHECK (Processed IN ('Y','N')) NOT NULL, TaxAmt NUMBER NOT NULL, TaxBaseAmt NUMBER NOT NULL, Updated DATE NOT NULL, UpdatedBy NUMBER(10) NOT NULL, CONSTRAINT LVE_CashTax_Key PRIMARY KEY (C_Cash_ID, C_Tax_ID))
;


-- Oct 20, 2014 5:45:59 PM VET
-- LVE ADempiere
CREATE TABLE I_Product_Category (AD_Client_ID NUMBER(10) NOT NULL, AD_Org_ID NUMBER(10) NOT NULL, Created DATE NOT NULL, CreatedBy NUMBER(10) NOT NULL, I_ErrorMsg NVARCHAR2(2000) DEFAULT NULL , I_IsImported CHAR(1) DEFAULT NULL  CHECK (I_IsImported IN ('Y','N')), I_Product_Category_ID NUMBER(10) NOT NULL, IsActive CHAR(1) CHECK (IsActive IN ('Y','N')) NOT NULL, M_Product_Category_ID NUMBER(10) DEFAULT NULL , Name NVARCHAR2(60) DEFAULT NULL , Processed CHAR(1) DEFAULT NULL  CHECK (Processed IN ('Y','N')), Processing CHAR(1) DEFAULT NULL , Updated DATE NOT NULL, UpdatedBy NUMBER(10) NOT NULL, Value NVARCHAR2(40) DEFAULT NULL , CONSTRAINT I_Product_Category_Key PRIMARY KEY (I_Product_Category_ID))
;


-- Dec 16, 2014 5:03:58 PM VET
-- LVE ADempiere
CREATE TABLE LVE_ProductReport (AD_Client_ID NUMBER(10) NOT NULL, AD_Org_ID NUMBER(10) NOT NULL, Created DATE NOT NULL, CreatedBy NUMBER(10) NOT NULL, Description NVARCHAR2(255) DEFAULT NULL , IsActive CHAR(1) DEFAULT 'Y' CHECK (IsActive IN ('Y','N')) NOT NULL, LVE_ProductReport_ID NUMBER(10) NOT NULL, Name NVARCHAR2(60) NOT NULL, Updated DATE NOT NULL, UpdatedBy NUMBER(10) NOT NULL, CONSTRAINT LVE_ProductReport_Key PRIMARY KEY (LVE_ProductReport_ID))
;

-- Dec 16, 2014 5:04:05 PM VET
-- LVE ADempiere
CREATE TABLE LVE_ProductReportLine (AD_Client_ID NUMBER(10) NOT NULL, AD_Org_ID NUMBER(10) NOT NULL, Created DATE NOT NULL, CreatedBy NUMBER(10) NOT NULL, IsActive CHAR(1) DEFAULT 'Y' CHECK (IsActive IN ('Y','N')) NOT NULL, LVE_ProductReport_ID NUMBER(10) NOT NULL, LVE_ProductReportLine_ID NUMBER(10) NOT NULL, M_Product_Category_ID NUMBER(10) DEFAULT NULL , M_Product_ID NUMBER(10) DEFAULT NULL , PrintName NVARCHAR2(60) DEFAULT NULL , SeqNo NUMBER(10) DEFAULT NULL , Updated DATE NOT NULL, UpdatedBy NUMBER(10) NOT NULL, CONSTRAINT LVE_ProductReportLine_Key PRIMARY KEY (LVE_ProductReportLine_ID))
;