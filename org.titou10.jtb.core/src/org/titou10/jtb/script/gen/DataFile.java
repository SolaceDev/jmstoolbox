//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.04.26 at 07:59:09 PM EDT 
//


package org.titou10.jtb.script.gen;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for dataFile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="dataFile">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="variablePrefix" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="delimiter" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="variableNames" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="fileName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="scriptLevel" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="charset" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "dataFile", propOrder = {
    "variablePrefix",
    "delimiter",
    "variableNames",
    "fileName",
    "scriptLevel",
    "charset"
})
public class DataFile {

    @XmlElement(required = true)
    protected String variablePrefix;
    @XmlElement(required = true)
    protected String delimiter;
    @XmlElement(required = true)
    protected String variableNames;
    @XmlElement(required = true)
    protected String fileName;
    protected boolean scriptLevel;
    protected String charset;

    /**
     * Gets the value of the variablePrefix property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVariablePrefix() {
        return variablePrefix;
    }

    /**
     * Sets the value of the variablePrefix property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVariablePrefix(String value) {
        this.variablePrefix = value;
    }

    /**
     * Gets the value of the delimiter property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Sets the value of the delimiter property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDelimiter(String value) {
        this.delimiter = value;
    }

    /**
     * Gets the value of the variableNames property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVariableNames() {
        return variableNames;
    }

    /**
     * Sets the value of the variableNames property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVariableNames(String value) {
        this.variableNames = value;
    }

    /**
     * Gets the value of the fileName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the value of the fileName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFileName(String value) {
        this.fileName = value;
    }

    /**
     * Gets the value of the scriptLevel property.
     * 
     */
    public boolean isScriptLevel() {
        return scriptLevel;
    }

    /**
     * Sets the value of the scriptLevel property.
     * 
     */
    public void setScriptLevel(boolean value) {
        this.scriptLevel = value;
    }

    /**
     * Gets the value of the charset property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCharset() {
        return charset;
    }

    /**
     * Sets the value of the charset property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCharset(String value) {
        this.charset = value;
    }

}
