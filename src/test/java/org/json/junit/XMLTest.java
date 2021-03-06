package org.json.junit;

/*
Copyright (c) 2020 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.json.JSONTokener;
import org.json.XML;
import org.json.XMLParserConfiguration;
import org.json.XMLXsiTypeConverter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


/**
 * Tests for JSON-Java XML.java
 * Note: noSpace() will be tested by JSONMLTest
 */
public class XMLTest {
    /**
     * JUnit supports temporary files and folders that are cleaned up after the test.
     * https://garygregory.wordpress.com/2010/01/20/junit-tip-use-rules-to-manage-temporary-files-and-folders/ 
     */
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    /**
     * JSONObject from a null XML string.
     * Expects a NullPointerException
     */
    @Test(expected=NullPointerException.class)
    public void shouldHandleNullXML() {
        String xmlStr = null;
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("jsonObject should be empty", jsonObject.isEmpty());
    }

    /**
     * Empty JSONObject from an empty XML string.
     */
    @Test
    public void shouldHandleEmptyXML() {

        String xmlStr = "";
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("jsonObject should be empty", jsonObject.isEmpty());
    }

    /**
     * Empty JSONObject from a non-XML string.
     */
    @Test
    public void shouldHandleNonXML() {
        String xmlStr = "{ \"this is\": \"not xml\"}";
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("xml string should be empty", jsonObject.isEmpty());
    }

    /**
     * Invalid XML string (tag contains a frontslash).
     * Expects a JSONException
     */
    @Test
    public void shouldHandleInvalidSlashInTag() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "    <address>\n"+
            "       <name/x>\n"+
            "       <street>abc street</street>\n"+
            "   </address>\n"+
            "</addresses>";
        try {
            XML.toJSONObject(xmlStr);
            fail("Expecting a JSONException");
        } catch (JSONException e) {
            assertEquals("Expecting an exception message",
                    "Misshaped tag at 176 [character 14 line 4]",
                    e.getMessage());
        }
    }

    /**
     * Invalid XML string ('!' char in tag)
     * Expects a JSONException
     */
    @Test
    public void shouldHandleInvalidBangInTag() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "    <address>\n"+
            "       <name/>\n"+
            "       <!>\n"+
            "   </address>\n"+
            "</addresses>";
        try {
            XML.toJSONObject(xmlStr);
            fail("Expecting a JSONException");
        } catch (JSONException e) {
            assertEquals("Expecting an exception message",
                    "Misshaped meta tag at 214 [character 12 line 7]",
                    e.getMessage());
        }
    }

    /**
     * Invalid XML string ('!' char and no closing tag brace)
     * Expects a JSONException
     */
    @Test
    public void shouldHandleInvalidBangNoCloseInTag() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "    <address>\n"+
            "       <name/>\n"+
            "       <!\n"+
            "   </address>\n"+
            "</addresses>";
        try {
            XML.toJSONObject(xmlStr);
            fail("Expecting a JSONException");
        } catch (JSONException e) {
            assertEquals("Expecting an exception message",
                    "Misshaped meta tag at 213 [character 12 line 7]",
                    e.getMessage());
        }
    }

    /**
     * Invalid XML string (no end brace for tag)
     * Expects JSONException
     */
    @Test
    public void shouldHandleNoCloseStartTag() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "    <address>\n"+
            "       <name/>\n"+
            "       <abc\n"+
            "   </address>\n"+
            "</addresses>";
        try {
            XML.toJSONObject(xmlStr);
            fail("Expecting a JSONException");
        } catch (JSONException e) {
            assertEquals("Expecting an exception message",
                    "Misplaced '<' at 193 [character 4 line 6]",
                    e.getMessage());
        }
    }

    /**
     * Invalid XML string (partial CDATA chars in tag name)
     * Expects JSONException
     */
    @Test
    public void shouldHandleInvalidCDATABangInTag() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "    <address>\n"+
            "       <name>Joe Tester</name>\n"+
            "       <![[]>\n"+
            "   </address>\n"+
            "</addresses>";
        try {
            XML.toJSONObject(xmlStr);
            fail("Expecting a JSONException");
        } catch (JSONException e) {
            assertEquals("Expecting an exception message",
                    "Expected 'CDATA[' at 204 [character 11 line 5]",
                    e.getMessage());
        }
    }

    /**
     * Null JSONObject in XML.toString()
     */
    @Test
    public void shouldHandleNullJSONXML() {
        JSONObject jsonObject= null;
        String actualXml=XML.toString(jsonObject);
        assertEquals("generated XML does not equal expected XML","\"null\"",actualXml);
    }

    /**
     * Empty JSONObject in XML.toString()
     */
    @Test
    public void shouldHandleEmptyJSONXML() {
        JSONObject jsonObject= new JSONObject();
        String xmlStr = XML.toString(jsonObject);
        assertTrue("xml string should be empty", xmlStr.isEmpty());
    }

    /**
     * No SML start tag. The ending tag ends up being treated as content.
     */
    @Test
    public void shouldHandleNoStartTag() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "    <address>\n"+
            "       <name/>\n"+
            "       <nocontent/>>\n"+
            "   </address>\n"+
            "</addresses>";
        String expectedStr = 
            "{\"addresses\":{\"address\":{\"name\":\"\",\"nocontent\":\"\",\""+
            "content\":\">\"},\"xsi:noNamespaceSchemaLocation\":\"test.xsd\",\""+
            "xmlns:xsi\":\"http://www.w3.org/2001/XMLSchema-instance\"}}";
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
    }

    /**
     * Valid XML to JSONObject
     */
    @Test
    public void shouldHandleSimpleXML() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "   <address>\n"+
            "       <name>Joe Tester</name>\n"+
            "       <street>[CDATA[Baker street 5]</street>\n"+
            "       <NothingHere/>\n"+
            "       <TrueValue>true</TrueValue>\n"+
            "       <FalseValue>false</FalseValue>\n"+
            "       <NullValue>null</NullValue>\n"+
            "       <PositiveValue>42</PositiveValue>\n"+
            "       <NegativeValue>-23</NegativeValue>\n"+
            "       <DoubleValue>-23.45</DoubleValue>\n"+
            "       <Nan>-23x.45</Nan>\n"+
            "       <ArrayOfNum>1, 2, 3, 4.1, 5.2</ArrayOfNum>\n"+
            "   </address>\n"+
            "</addresses>";

        String expectedStr = 
            "{\"addresses\":{\"address\":{\"street\":\"[CDATA[Baker street 5]\","+
            "\"name\":\"Joe Tester\",\"NothingHere\":\"\",TrueValue:true,\n"+
            "\"FalseValue\":false,\"NullValue\":null,\"PositiveValue\":42,\n"+
            "\"NegativeValue\":-23,\"DoubleValue\":-23.45,\"Nan\":-23x.45,\n"+
            "\"ArrayOfNum\":\"1, 2, 3, 4.1, 5.2\"\n"+
            "},\"xsi:noNamespaceSchemaLocation\":"+
            "\"test.xsd\",\"xmlns:xsi\":\"http://www.w3.org/2001/"+
            "XMLSchema-instance\"}}";

        compareStringToJSONObject(xmlStr, expectedStr);
        compareReaderToJSONObject(xmlStr, expectedStr);
        compareFileToJSONObject(xmlStr, expectedStr);
    }

    /**
     * Tests to verify that supported escapes in XML are converted to actual values.
     */
    @Test
    public void testXmlEscapeToJson(){
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<root>"+
            "<rawQuote>\"</rawQuote>"+
            "<euro>A &#8364;33</euro>"+
            "<euroX>A &#x20ac;22&#x20AC;</euroX>"+
            "<unknown>some text &copy;</unknown>"+
            "<known>&#x0022; &quot; &amp; &apos; &lt; &gt;</known>"+
            "<high>&#x1D122; &#x10165;</high>" +
            "</root>";
        String expectedStr = 
            "{\"root\":{" +
            "\"rawQuote\":\"\\\"\"," +
            "\"euro\":\"A €33\"," +
            "\"euroX\":\"A €22€\"," +
            "\"unknown\":\"some text &copy;\"," +
            "\"known\":\"\\\" \\\" & ' < >\"," +
            "\"high\":\"𝄢 𐅥\""+
            "}}";
        
        compareStringToJSONObject(xmlStr, expectedStr);
        compareReaderToJSONObject(xmlStr, expectedStr);
        compareFileToJSONObject(xmlStr, expectedStr);
    }
    
    /**
     * Tests that control characters are escaped.
     */
    @Test
    public void testJsonToXmlEscape(){
        final String jsonSrc = "{\"amount\":\"10,00 €\","
                + "\"description\":\"Ação Válida\u0085\","
                + "\"xmlEntities\":\"\\\" ' & < >\""
                + "}";
        JSONObject json = new JSONObject(jsonSrc);
        String xml = XML.toString(json);
        //test control character not existing
        assertFalse("Escaping \u0085 failed. Found in XML output.", xml.contains("\u0085"));
        assertTrue("Escaping \u0085 failed. Entity not found in XML output.", xml.contains("&#x85;"));
        // test normal unicode existing
        assertTrue("Escaping € failed. Not found in XML output.", xml.contains("€"));
        assertTrue("Escaping ç failed. Not found in XML output.", xml.contains("ç"));
        assertTrue("Escaping ã failed. Not found in XML output.", xml.contains("ã"));
        assertTrue("Escaping á failed. Not found in XML output.", xml.contains("á"));
        // test XML Entities converted
        assertTrue("Escaping \" failed. Not found in XML output.", xml.contains("&quot;"));
        assertTrue("Escaping ' failed. Not found in XML output.", xml.contains("&apos;"));
        assertTrue("Escaping & failed. Not found in XML output.", xml.contains("&amp;"));
        assertTrue("Escaping < failed. Not found in XML output.", xml.contains("&lt;"));
        assertTrue("Escaping > failed. Not found in XML output.", xml.contains("&gt;"));
    }

    /**
     * Valid XML with comments to JSONObject
     */
    @Test
    public void shouldHandleCommentsInXML() {

        String xmlStr = 
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
                "<!-- this is a comment -->\n"+
                "<addresses>\n"+
                "   <address>\n"+
                "       <![CDATA[ this is -- <another> comment ]]>\n"+
                "       <name>Joe Tester</name>\n"+
                "       <!-- this is a - multi line \n"+
                "            comment -->\n"+
                "       <street>Baker street 5</street>\n"+
                "   </address>\n"+
                "</addresses>";
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        String expectedStr = "{\"addresses\":{\"address\":{\"street\":\"Baker "+
                "street 5\",\"name\":\"Joe Tester\",\"content\":\" this is -- "+
                "<another> comment \"}}}";
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
    }

    /**
     * Valid XML to XML.toString()
     */
    @Test
    public void shouldHandleToString() {
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<addresses xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""+
            "   xsi:noNamespaceSchemaLocation='test.xsd'>\n"+
            "   <address>\n"+
            "       <name>[CDATA[Joe &amp; T &gt; e &lt; s &quot; t &apos; er]]</name>\n"+
            "       <street>Baker street 5</street>\n"+
            "       <ArrayOfNum>1, 2, 3, 4.1, 5.2</ArrayOfNum>\n"+
            "   </address>\n"+
            "</addresses>";

        String expectedStr = 
                "{\"addresses\":{\"address\":{\"street\":\"Baker street 5\","+
                "\"name\":\"[CDATA[Joe & T > e < s \\\" t \\\' er]]\","+
                "\"ArrayOfNum\":\"1, 2, 3, 4.1, 5.2\"\n"+
                "},\"xsi:noNamespaceSchemaLocation\":"+
                "\"test.xsd\",\"xmlns:xsi\":\"http://www.w3.org/2001/"+
                "XMLSchema-instance\"}}";
        
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        String xmlToStr = XML.toString(jsonObject);
        JSONObject finalJsonObject = XML.toJSONObject(xmlToStr);
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
        Util.compareActualVsExpectedJsonObjects(finalJsonObject,expectedJsonObject);
    }

    /**
     * Converting a JSON doc containing '>' content to JSONObject, then
     * XML.toString() should result in valid XML.
     */
    @Test
    public void shouldHandleContentNoArraytoString() {
        String expectedStr = "{\"addresses\":{\"content\":\">\"}}";
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        String finalStr = XML.toString(expectedJsonObject);
        String expectedFinalStr = "<addresses>&gt;</addresses>";
        assertEquals("Should handle expectedFinal: ["+expectedStr+"] final: ["+
                finalStr+"]", expectedFinalStr, finalStr);
    }

    /**
     * Converting a JSON doc containing a 'content' array to JSONObject, then
     * XML.toString() should result in valid XML.
     * TODO: This is probably an error in how the 'content' keyword is used.
     */
    @Test
    public void shouldHandleContentArraytoString() {
        String expectedStr = 
            "{\"addresses\":{" +
            "\"content\":[1, 2, 3]}}";
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        String finalStr = XML.toString(expectedJsonObject);
        String expectedFinalStr = "<addresses>"+
                "1\n2\n3</addresses>";
        assertEquals("Should handle expectedFinal: ["+expectedStr+"] final: ["+
                finalStr+"]", expectedFinalStr, finalStr);
    }

    /**
     * Converting a JSON doc containing a named array to JSONObject, then
     * XML.toString() should result in valid XML.
     */
    @Test
    public void shouldHandleArraytoString() {
        String expectedStr = 
            "{\"addresses\":{"+
            "\"something\":[1, 2, 3]}}";
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        String finalStr = XML.toString(expectedJsonObject);
        String expectedFinalStr = "<addresses>"+
                "<something>1</something><something>2</something><something>3</something>"+
                "</addresses>";
        assertEquals("Should handle expectedFinal: ["+expectedStr+"] final: ["+
                finalStr+"]", expectedFinalStr, finalStr);
    }
    
    /**
     * Tests that the XML output for empty arrays is consistent.
     */
    @Test
    public void shouldHandleEmptyArray(){
        final JSONObject jo1 = new JSONObject();
        jo1.put("array",new Object[]{});
        final JSONObject jo2 = new JSONObject();
        jo2.put("array",new JSONArray());

        final String expected = "<jo></jo>";
        String output1 = XML.toString(jo1,"jo");
        assertEquals("Expected an empty root tag", expected, output1);
        String output2 = XML.toString(jo2,"jo");
        assertEquals("Expected an empty root tag", expected, output2);
    }
    
    /**
     * Tests that the XML output for arrays is consistent when an internal array is empty.
     */
    @Test
    public void shouldHandleEmptyMultiArray(){
        final JSONObject jo1 = new JSONObject();
        jo1.put("arr",new Object[]{"One", new String[]{}, "Four"});
        final JSONObject jo2 = new JSONObject();
        jo2.put("arr",new JSONArray(new Object[]{"One", new JSONArray(new String[]{}), "Four"}));

        final String expected = "<jo><arr>One</arr><arr></arr><arr>Four</arr></jo>";
        String output1 = XML.toString(jo1,"jo");
        assertEquals("Expected a matching array", expected, output1);
        String output2 = XML.toString(jo2,"jo");
        assertEquals("Expected a matching array", expected, output2);
    }
   
    /**
     * Tests that the XML output for arrays is consistent when arrays are not empty.
     */
    @Test
    public void shouldHandleNonEmptyArray(){
        final JSONObject jo1 = new JSONObject();
        jo1.put("arr",new String[]{"One", "Two", "Three"});
        final JSONObject jo2 = new JSONObject();
        jo2.put("arr",new JSONArray(new String[]{"One", "Two", "Three"}));

        final String expected = "<jo><arr>One</arr><arr>Two</arr><arr>Three</arr></jo>";
        String output1 = XML.toString(jo1,"jo");
        assertEquals("Expected a non empty root tag", expected, output1);
        String output2 = XML.toString(jo2,"jo");
        assertEquals("Expected a non empty root tag", expected, output2);
    }

    /**
     * Tests that the XML output for arrays is consistent when arrays are not empty and contain internal arrays.
     */
    @Test
    public void shouldHandleMultiArray(){
        final JSONObject jo1 = new JSONObject();
        jo1.put("arr",new Object[]{"One", new String[]{"Two", "Three"}, "Four"});
        final JSONObject jo2 = new JSONObject();
        jo2.put("arr",new JSONArray(new Object[]{"One", new JSONArray(new String[]{"Two", "Three"}), "Four"}));

        final String expected = "<jo><arr>One</arr><arr><array>Two</array><array>Three</array></arr><arr>Four</arr></jo>";
        String output1 = XML.toString(jo1,"jo");
        assertEquals("Expected a matching array", expected, output1);
        String output2 = XML.toString(jo2,"jo");
        assertEquals("Expected a matching array", expected, output2);
    }

    /**
     * Converting a JSON doc containing a named array of nested arrays to
     * JSONObject, then XML.toString() should result in valid XML.
     */
    @Test
    public void shouldHandleNestedArraytoString() {
        String xmlStr = 
            "{\"addresses\":{\"address\":{\"name\":\"\",\"nocontent\":\"\","+
            "\"outer\":[[1], [2], [3]]},\"xsi:noNamespaceSchemaLocation\":\"test.xsd\",\""+
            "xmlns:xsi\":\"http://www.w3.org/2001/XMLSchema-instance\"}}";
        JSONObject jsonObject = new JSONObject(xmlStr);
        String finalStr = XML.toString(jsonObject);
        JSONObject finalJsonObject = XML.toJSONObject(finalStr);
        String expectedStr = "<addresses><address><name/><nocontent/>"+
                "<outer><array>1</array></outer><outer><array>2</array>"+
                "</outer><outer><array>3</array></outer>"+
                "</address><xsi:noNamespaceSchemaLocation>test.xsd</xsi:noName"+
                "spaceSchemaLocation><xmlns:xsi>http://www.w3.org/2001/XMLSche"+
                "ma-instance</xmlns:xsi></addresses>";
        JSONObject expectedJsonObject = XML.toJSONObject(expectedStr);
        Util.compareActualVsExpectedJsonObjects(finalJsonObject,expectedJsonObject);
    }


    /**
     * Possible bug: 
     * Illegal node-names must be converted to legal XML-node-names.
     * The given example shows 2 nodes which are valid for JSON, but not for XML.
     * Therefore illegal arguments should be converted to e.g. an underscore (_).
     */
    @Test
    public void shouldHandleIllegalJSONNodeNames()
    {
        JSONObject inputJSON = new JSONObject();
        inputJSON.append("123IllegalNode", "someValue1");
        inputJSON.append("Illegal@node", "someValue2");

        String result = XML.toString(inputJSON);

        /*
         * This is invalid XML. Names should not begin with digits or contain
         * certain values, including '@'. One possible solution is to replace
         * illegal chars with '_', in which case the expected output would be:
         * <___IllegalNode>someValue1</___IllegalNode><Illegal_node>someValue2</Illegal_node>
         */
        String expected = "<123IllegalNode>someValue1</123IllegalNode><Illegal@node>someValue2</Illegal@node>";

        assertEquals("length",expected.length(), result.length());
        assertTrue("123IllegalNode",result.contains("<123IllegalNode>someValue1</123IllegalNode>"));
        assertTrue("Illegal@node",result.contains("<Illegal@node>someValue2</Illegal@node>"));
    }

    /**
     * JSONObject with NULL value, to XML.toString()
     */
    @Test
    public void shouldHandleNullNodeValue()
    {
        JSONObject inputJSON = new JSONObject();
        inputJSON.put("nullValue", JSONObject.NULL);
        // This is a possible preferred result
        // String expectedXML = "<nullValue/>";
        /**
         * This is the current behavior. JSONObject.NULL is emitted as 
         * the string, "null".
         */
        String actualXML = "<nullValue>null</nullValue>";
        String resultXML = XML.toString(inputJSON);
        assertEquals(actualXML, resultXML);
    }

    /**
     * Investigate exactly how the "content" keyword works
     */
    @Test
    public void contentOperations() {
        /*
         * When a standalone <!CDATA[...]] structure is found while parsing XML into a
         * JSONObject, the contents are placed in a string value with key="content".
         */
        String xmlStr = "<tag1></tag1><![CDATA[if (a < b && a > 0) then return]]><tag2></tag2>";
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("1. 3 items", 3 == jsonObject.length());
        assertTrue("1. empty tag1", "".equals(jsonObject.get("tag1")));
        assertTrue("1. empty tag2", "".equals(jsonObject.get("tag2")));
        assertTrue("1. content found", "if (a < b && a > 0) then return".equals(jsonObject.get("content")));

        // multiple consecutive standalone cdatas are accumulated into an array
        xmlStr = "<tag1></tag1><![CDATA[if (a < b && a > 0) then return]]><tag2></tag2><![CDATA[here is another cdata]]>";
        jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("2. 3 items", 3 == jsonObject.length());
        assertTrue("2. empty tag1", "".equals(jsonObject.get("tag1")));
        assertTrue("2. empty tag2", "".equals(jsonObject.get("tag2")));
        assertTrue("2. content array found", jsonObject.get("content") instanceof JSONArray);
        JSONArray jsonArray = jsonObject.getJSONArray("content");
        assertTrue("2. array size", jsonArray.length() == 2);
        assertTrue("2. content array entry 0", "if (a < b && a > 0) then return".equals(jsonArray.get(0)));
        assertTrue("2. content array entry 1", "here is another cdata".equals(jsonArray.get(1)));

        /*
         * text content is accumulated in a "content" inside a local JSONObject.
         * If there is only one instance, it is saved in the context (a different JSONObject 
         * from the calling code. and the content element is discarded. 
         */
        xmlStr =  "<tag1>value 1</tag1>";
        jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("3. 2 items", 1 == jsonObject.length());
        assertTrue("3. value tag1", "value 1".equals(jsonObject.get("tag1")));

        /*
         * array-style text content (multiple tags with the same name) is 
         * accumulated in a local JSONObject with key="content" and value=JSONArray,
         * saved in the context, and then the local JSONObject is discarded.
         */
        xmlStr =  "<tag1>value 1</tag1><tag1>2</tag1><tag1>true</tag1>";
        jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("4. 1 item", 1 == jsonObject.length());
        assertTrue("4. content array found", jsonObject.get("tag1") instanceof JSONArray);
        jsonArray = jsonObject.getJSONArray("tag1");
        assertTrue("4. array size", jsonArray.length() == 3);
        assertTrue("4. content array entry 0", "value 1".equals(jsonArray.get(0)));
        assertTrue("4. content array entry 1", jsonArray.getInt(1) == 2);
        assertTrue("4. content array entry 2", jsonArray.getBoolean(2) == true);

        /*
         * Complex content is accumulated in a "content" field. For example, an element
         * may contain a mix of child elements and text. Each text segment is 
         * accumulated to content. 
         */
        xmlStr =  "<tag1>val1<tag2/>val2</tag1>";
        jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("5. 1 item", 1 == jsonObject.length());
        assertTrue("5. jsonObject found", jsonObject.get("tag1") instanceof JSONObject);
        jsonObject = jsonObject.getJSONObject("tag1");
        assertTrue("5. 2 contained items", 2 == jsonObject.length());
        assertTrue("5. contained tag", "".equals(jsonObject.get("tag2")));
        assertTrue("5. contained content jsonArray found", jsonObject.get("content") instanceof JSONArray);
        jsonArray = jsonObject.getJSONArray("content");
        assertTrue("5. array size", jsonArray.length() == 2);
        assertTrue("5. content array entry 0", "val1".equals(jsonArray.get(0)));
        assertTrue("5. content array entry 1", "val2".equals(jsonArray.get(1)));

        /*
         * If there is only 1 complex text content, then it is accumulated in a 
         * "content" field as a string.
         */
        xmlStr =  "<tag1>val1<tag2/></tag1>";
        jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("6. 1 item", 1 == jsonObject.length());
        assertTrue("6. jsonObject found", jsonObject.get("tag1") instanceof JSONObject);
        jsonObject = jsonObject.getJSONObject("tag1");
        assertTrue("6. contained content found", "val1".equals(jsonObject.get("content")));
        assertTrue("6. contained tag2", "".equals(jsonObject.get("tag2")));

        /*
         * In this corner case, the content sibling happens to have key=content
         * We end up with an array within an array, and no content element.
         * This is probably a bug. 
         */
        xmlStr =  "<tag1>val1<content/></tag1>";
        jsonObject = XML.toJSONObject(xmlStr);
        assertTrue("7. 1 item", 1 == jsonObject.length());
        assertTrue("7. jsonArray found", jsonObject.get("tag1") instanceof JSONArray);
        jsonArray = jsonObject.getJSONArray("tag1");
        assertTrue("array size 1", jsonArray.length() == 1);
        assertTrue("7. contained array found", jsonArray.get(0) instanceof JSONArray);
        jsonArray = jsonArray.getJSONArray(0);
        assertTrue("7. inner array size 2", jsonArray.length() == 2);
        assertTrue("7. inner array item 0", "val1".equals(jsonArray.get(0)));
        assertTrue("7. inner array item 1", "".equals(jsonArray.get(1)));

        /*
         * Confirm behavior of original issue
         */
        String jsonStr = 
                "{"+
                    "\"Profile\": {"+
                        "\"list\": {"+
                            "\"history\": {"+
                                "\"entries\": ["+
                                    "{"+
                                        "\"deviceId\": \"id\","+
                                        "\"content\": {"+
                                            "\"material\": ["+
                                                "{"+
                                                    "\"stuff\": false"+
                                                "}"+
                                            "]"+
                                        "}"+
                                    "}"+
                                "]"+
                            "}"+
                        "}"+
                    "}"+
                "}";
        jsonObject = new JSONObject(jsonStr);
        xmlStr = XML.toString(jsonObject);
        /*
         * This is the created XML. Looks like content was mistaken for
         * complex (child node + text) XML. 
         *  <Profile>
         *      <list>
         *          <history>
         *              <entries>
         *                  <deviceId>id</deviceId>
         *                  {&quot;material&quot;:[{&quot;stuff&quot;:false}]}
         *              </entries>
         *          </history>
         *      </list>
         *  </Profile>
         */
        assertTrue("nothing to test here, see comment on created XML, above", true);
    }

    /**
     * Convenience method, given an input string and expected result,
     * convert to JSONObject and compare actual to expected result.
     * @param xmlStr the string to parse
     * @param expectedStr the expected JSON string
     */
    private void compareStringToJSONObject(String xmlStr, String expectedStr) {
        JSONObject jsonObject = XML.toJSONObject(xmlStr);
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
    }

    /**
     * Convenience method, given an input string and expected result,
     * convert to JSONObject via reader and compare actual to expected result.
     * @param xmlStr the string to parse
     * @param expectedStr the expected JSON string
     */
    private void compareReaderToJSONObject(String xmlStr, String expectedStr) {
        JSONObject expectedJsonObject = new JSONObject(expectedStr);
        Reader reader = new StringReader(xmlStr);
        JSONObject jsonObject = XML.toJSONObject(reader);
        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
    }

    /**
     * Convenience method, given an input string and expected result, convert to
     * JSONObject via file and compare actual to expected result.
     * 
     * @param xmlStr
     *            the string to parse
     * @param expectedStr
     *            the expected JSON string
     * @throws IOException
     */
    private void compareFileToJSONObject(String xmlStr, String expectedStr) {
        try {
            JSONObject expectedJsonObject = new JSONObject(expectedStr);
            File tempFile = this.testFolder.newFile("fileToJSONObject.xml");
            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write(xmlStr);
            } finally {
                fileWriter.close();
            }

            Reader reader = new FileReader(tempFile);
            try {
                JSONObject jsonObject = XML.toJSONObject(reader);
                Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            fail("Error: " +e.getMessage());
        }
    }

    /**
     * JSON string lost leading zero and converted "True" to true.
     */
    @Test
    public void testToJSONArray_jsonOutput() {
        final String originalXml = "<root><id>01</id><id>1</id><id>00</id><id>0</id><item id=\"01\"/><title>True</title></root>";
        final JSONObject expectedJson = new JSONObject("{\"root\":{\"item\":{\"id\":\"01\"},\"id\":[\"01\",1,\"00\",0],\"title\":true}}");
        final JSONObject actualJsonOutput = XML.toJSONObject(originalXml, false);

        Util.compareActualVsExpectedJsonObjects(actualJsonOutput,expectedJson);
    }

    /**
     * JSON string cannot be reverted to original xml.
     */
    @Test
    public void testToJSONArray_reversibility() {
        final String originalXml = "<root><id>01</id><id>1</id><id>00</id><id>0</id><item id=\"01\"/><title>True</title></root>";
        final String revertedXml = XML.toString(XML.toJSONObject(originalXml, false));

        assertNotEquals(revertedXml, originalXml);
    }

    /**
     * test passes when using the new method toJsonArray.
     */
    @Test
    public void testToJsonXML() {
        final String originalXml = "<root><id>01</id><id>1</id><id>00</id><id>0</id><item id=\"01\"/><title>True</title></root>";
        final JSONObject expected = new JSONObject("{\"root\":{\"item\":{\"id\":\"01\"},\"id\":[\"01\",\"1\",\"00\",\"0\"],\"title\":\"True\"}}");

        final JSONObject actual = XML.toJSONObject(originalXml,true);
        
        Util.compareActualVsExpectedJsonObjects(actual, expected);
        
        final String reverseXml = XML.toString(actual);
        // this reversal isn't exactly the same. use JSONML for an exact reversal
        // the order of the elements may be differnet as well.
        final String expectedReverseXml = "<root><item><id>01</id></item><id>01</id><id>1</id><id>00</id><id>0</id><title>True</title></root>";

        assertEquals("length",expectedReverseXml.length(), reverseXml.length());
        assertTrue("array contents", reverseXml.contains("<id>01</id><id>1</id><id>00</id><id>0</id>"));
        assertTrue("item contents", reverseXml.contains("<item><id>01</id></item>"));
        assertTrue("title contents", reverseXml.contains("<title>True</title>"));
    }
    
    /**
     * test to validate certain conditions of XML unescaping.
     */
    @Test
    public void testUnescape() {
        assertEquals("{\"xml\":\"Can cope <;\"}",
                XML.toJSONObject("<xml>Can cope &lt;; </xml>").toString());
        assertEquals("Can cope <; ", XML.unescape("Can cope &lt;; "));

        assertEquals("{\"xml\":\"Can cope & ;\"}",
                XML.toJSONObject("<xml>Can cope &amp; ; </xml>").toString());
        assertEquals("Can cope & ; ", XML.unescape("Can cope &amp; ; "));

        assertEquals("{\"xml\":\"Can cope &;\"}",
                XML.toJSONObject("<xml>Can cope &amp;; </xml>").toString());
        assertEquals("Can cope &; ", XML.unescape("Can cope &amp;; "));

        // unicode entity
        assertEquals("{\"xml\":\"Can cope 4;\"}",
                XML.toJSONObject("<xml>Can cope &#x34;; </xml>").toString());
        assertEquals("Can cope 4; ", XML.unescape("Can cope &#x34;; "));

        // double escaped
        assertEquals("{\"xml\":\"Can cope &lt;\"}",
                XML.toJSONObject("<xml>Can cope &amp;lt; </xml>").toString());
        assertEquals("Can cope &lt; ", XML.unescape("Can cope &amp;lt; "));
        
        assertEquals("{\"xml\":\"Can cope &#x34;\"}",
                XML.toJSONObject("<xml>Can cope &amp;#x34; </xml>").toString());
        assertEquals("Can cope &#x34; ", XML.unescape("Can cope &amp;#x34; "));

   }

    /**
     * test passes when xsi:nil="true" converting to null (JSON specification-like nil conversion enabled)
     */
    @Test
    public void testToJsonWithNullWhenNilConversionEnabled() {
        final String originalXml = "<root><id xsi:nil=\"true\"/></root>";
        final String expectedJsonString = "{\"root\":{\"id\":null}}";

        final JSONObject json = XML.toJSONObject(originalXml,
                new XMLParserConfiguration()
                    .withKeepStrings(false)
                    .withcDataTagName("content")
                    .withConvertNilAttributeToNull(true));
        assertEquals(expectedJsonString, json.toString());
    }

    /**
     * test passes when xsi:nil="true" not converting to null (JSON specification-like nil conversion disabled)
     */
    @Test
    public void testToJsonWithNullWhenNilConversionDisabled() {
        final String originalXml = "<root><id xsi:nil=\"true\"/></root>";
        final String expectedJsonString = "{\"root\":{\"id\":{\"xsi:nil\":true}}}";

        final JSONObject json = XML.toJSONObject(originalXml, new XMLParserConfiguration());
        assertEquals(expectedJsonString, json.toString());
    }

    /**
     * Tests to verify that supported escapes in XML are converted to actual values.
     */
    @Test
    public void testIssue537CaseSensitiveHexEscapeMinimal(){
        String xmlStr = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<root>Neutrophils.Hypersegmented &#X7C; Bld-Ser-Plas</root>";
        String expectedStr = 
            "{\"root\":\"Neutrophils.Hypersegmented | Bld-Ser-Plas\"}";
        JSONObject xmlJSONObj = XML.toJSONObject(xmlStr, true);
        JSONObject expected = new JSONObject(expectedStr);
        Util.compareActualVsExpectedJsonObjects(xmlJSONObj, expected);
    }

    /**
     * Tests to verify that supported escapes in XML are converted to actual values.
     */
    @Test
    public void testIssue537CaseSensitiveHexEscapeFullFile(){
        try {
            InputStream xmlStream = null;
            try {
                xmlStream = XMLTest.class.getClassLoader().getResourceAsStream("Issue537.xml");
                Reader xmlReader = new InputStreamReader(xmlStream);
                JSONObject actual = XML.toJSONObject(xmlReader, true);
                InputStream jsonStream = null;
                try {
                    jsonStream = XMLTest.class.getClassLoader().getResourceAsStream("Issue537.json");
                    final JSONObject expected = new JSONObject(new JSONTokener(jsonStream));
                    Util.compareActualVsExpectedJsonObjects(actual,expected);
                } finally {
                    if (jsonStream != null) {
                        jsonStream.close();
                    }
                }
            } finally {
                if (xmlStream != null) {
                    xmlStream.close();
                }
            }
        } catch (IOException e) {
            fail("file writer error: " +e.getMessage());
        }
    }

    /**
     * Tests to verify that supported escapes in XML are converted to actual values.
     */
    @Test
    public void testIssue537CaseSensitiveHexUnEscapeDirect(){
        String origStr = 
            "Neutrophils.Hypersegmented &#X7C; Bld-Ser-Plas";
        String expectedStr = 
            "Neutrophils.Hypersegmented | Bld-Ser-Plas";
        String actualStr = XML.unescape(origStr);
        
        assertEquals("Case insensitive Entity unescape",  expectedStr, actualStr);
    }

    /**
     * test passes when xsi:type="java.lang.String" not converting to string
     */
    @Test
    public void testToJsonWithTypeWhenTypeConversionDisabled() {
        String originalXml = "<root><id xsi:type=\"string\">1234</id></root>";
        String expectedJsonString = "{\"root\":{\"id\":{\"xsi:type\":\"string\",\"content\":1234}}}";
        JSONObject expectedJson = new JSONObject(expectedJsonString);
        JSONObject actualJson = XML.toJSONObject(originalXml, new XMLParserConfiguration());
        Util.compareActualVsExpectedJsonObjects(actualJson,expectedJson);
    }

    /**
     * test passes when xsi:type="java.lang.String" converting to String
     */
    @Test
    public void testToJsonWithTypeWhenTypeConversionEnabled() {
        String originalXml = "<root><id1 xsi:type=\"string\">1234</id1>"
                + "<id2 xsi:type=\"integer\">1234</id2></root>";
        String expectedJsonString = "{\"root\":{\"id2\":1234,\"id1\":\"1234\"}}";
        JSONObject expectedJson = new JSONObject(expectedJsonString);
        Map<String, XMLXsiTypeConverter<?>> xsiTypeMap = new HashMap<String, XMLXsiTypeConverter<?>>();
        xsiTypeMap.put("string", new XMLXsiTypeConverter<String>() {
            @Override public String convert(final String value) {
                return value;
            }
        });
        xsiTypeMap.put("integer", new XMLXsiTypeConverter<Integer>() {
            @Override public Integer convert(final String value) {
                return Integer.valueOf(value);
            }
        });
        JSONObject actualJson = XML.toJSONObject(originalXml, new XMLParserConfiguration().withXsiTypeMap(xsiTypeMap));
        Util.compareActualVsExpectedJsonObjects(actualJson,expectedJson);
    }

    @Test
    public void testToJsonWithXSITypeWhenTypeConversionEnabled() {
        String originalXml = "<root><asString xsi:type=\"string\">12345</asString><asInt "
                + "xsi:type=\"integer\">54321</asInt></root>";
        String expectedJsonString = "{\"root\":{\"asString\":\"12345\",\"asInt\":54321}}";
        JSONObject expectedJson = new JSONObject(expectedJsonString);
        Map<String, XMLXsiTypeConverter<?>> xsiTypeMap = new HashMap<String, XMLXsiTypeConverter<?>>();
        xsiTypeMap.put("string", new XMLXsiTypeConverter<String>() {
            @Override public String convert(final String value) {
                return value;
            }
        });
        xsiTypeMap.put("integer", new XMLXsiTypeConverter<Integer>() {
            @Override public Integer convert(final String value) {
                return Integer.valueOf(value);
            }
        });
        JSONObject actualJson = XML.toJSONObject(originalXml, new XMLParserConfiguration().withXsiTypeMap(xsiTypeMap));
        Util.compareActualVsExpectedJsonObjects(actualJson,expectedJson);
    }

    @Test
    public void testToJsonWithXSITypeWhenTypeConversionNotEnabledOnOne() {
        String originalXml = "<root><asString xsi:type=\"string\">12345</asString><asInt>54321</asInt></root>";
        String expectedJsonString = "{\"root\":{\"asString\":\"12345\",\"asInt\":54321}}";
        JSONObject expectedJson = new JSONObject(expectedJsonString);
        Map<String, XMLXsiTypeConverter<?>> xsiTypeMap = new HashMap<String, XMLXsiTypeConverter<?>>();
        xsiTypeMap.put("string", new XMLXsiTypeConverter<String>() {
            @Override public String convert(final String value) {
                return value;
            }
        });
        JSONObject actualJson = XML.toJSONObject(originalXml, new XMLParserConfiguration().withXsiTypeMap(xsiTypeMap));
        Util.compareActualVsExpectedJsonObjects(actualJson,expectedJson);
    }

    @Test
    public void testXSITypeMapNotModifiable() {
        Map<String, XMLXsiTypeConverter<?>> xsiTypeMap = new HashMap<String, XMLXsiTypeConverter<?>>();
        XMLParserConfiguration config = new XMLParserConfiguration().withXsiTypeMap(xsiTypeMap);
        xsiTypeMap.put("string", new XMLXsiTypeConverter<String>() {
            @Override public String convert(final String value) {
                return value;
            }
        });
        assertEquals("Config Conversion Map size is expected to be 0", 0, config.getXsiTypeMap().size());

        try {
            config.getXsiTypeMap().put("boolean", new XMLXsiTypeConverter<Boolean>() {
                @Override public Boolean convert(final String value) {
                    return Boolean.valueOf(value);
                }
            });
            fail("Expected to be unable to modify the config");
        } catch (Exception ignored) { }
    }
    
    @Test
    public void testExtractJSONObject() {
        try {
            JSONObject expectedJsonObject = new JSONObject("{\"author\":\"Randall, Cynthia\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"When Carla meets Paul at an ornithology\\n      conference, tempers fly as feathers get ruffled.\",\"id\":\"bk106\",\"title\":\"Lover Birds\",\"publish_date\":\"2000-09-02\"}");
            File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications\n"
                		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,\n"
                		+ "      an evil sorceress, and her own childhood to become queen\n"
                		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology\n"
                		+ "      society in England, the young survivors lay the\n"
                		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious\n"
                		+ "      agent known only as Oberon helps to create a new life\n"
                		+ "      for the inhabitants of London. Sequel to Maeve\n"
                		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,\n"
                		+ "      battle one another for control of England. Sequel to\n"
                		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology\n"
                		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty\n"
                		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,\n"
                		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg\n"
                		+ "      Uncertainty Device, James Salway discovers the problems\n"
                		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in\n"
                		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in\n"
                		+ "      detail, with attention to XML DOM interfaces, XSLT processing,\n"
                		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,\n"
                		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are\n"
                		+ "      integrated into a comprehensive development\n"
                		+ "      environment.</description></book></catalog>\n"
                		+ "");
            } finally {
                fileWriter.close();
            }

            Reader reader = new FileReader(tempFile);
            try {
                JSONObject jsonObject = XML.toJSONObject(reader, new JSONPointer("/catalog/book/5"));
//                System.out.println(jsonObject);
                Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            fail("Error: " +e.getMessage());
        }
    }
    
    @Test
    public void testFailExtractJSONObject() {
        try {
            JSONObject expectedJsonObject = new JSONObject("{}");
            File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
            FileWriter fileWriter = new FileWriter(tempFile);
            try {
            	fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
                		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
                		+ "      an evil sorceress, and her own childhood to become queen"
                		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
                		+ "      society in England, the young survivors lay the"
                		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
                		+ "      agent known only as Oberon helps to create a new life"
                		+ "      for the inhabitants of London. Sequel to Maeve"
                		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
                		+ "      battle one another for control of England. Sequel to"
                		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
                		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
                		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
                		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
                		+ "      Uncertainty Device, James Salway discovers the problems"
                		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
                		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
                		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
                		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
                		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
                		+ "      integrated into a comprehensive development"
                		+ "      environment.</description></book></catalog>");
            } finally {
                fileWriter.close();
            }

            Reader reader = new FileReader(tempFile);
            try {
                JSONObject jsonObject = XML.toJSONObject(reader, new JSONPointer("/catalog/book/5"));
                assertNotEquals(jsonObject, expectedJsonObject);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            fail("Error: " +e.getMessage());
        }
    }
    @Test(expected=JSONException.class)
    public void testExtractJSONObjectThrowsSyntaxError() {
    	try {
    		
    	
    	File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
        FileWriter fileWriter = new FileWriter(tempFile);
        try {
        	fileWriter.write("<?xml version=\"1.0\"?><catalog><catalog>");
        	
        } finally {
            fileWriter.close();
        }
        

        Reader reader = new FileReader(tempFile);
        try {
            XML.toJSONObject(reader, new JSONPointer("/catalog/book/20"));
        } finally {
            reader.close();
        }
    	} catch (IOException e) {
            fail("Error: " +e.getMessage());
        } 
    } 
    
    @Test
    public void testJsonObjectReplace() {
        try {
            JSONObject expectedJsonObject = new JSONObject("{\"catalog\":{\"book\":[{\"author\":\"Gambardella, Matthew\",\"price\":44.95,\"genre\":\"Computer\",\"description\":\"An in-depth look at creating applications      with XML.\",\"id\":\"bk101\",\"title\":\"XML Developer's Guide\",\"publish_date\":\"2000-10-01\"},{\"author\":\"Ralls, Kim\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"A former architect battles corporate zombies,      an evil sorceress, and her own childhood to become queen      of the world.\",\"id\":\"bk102\",\"title\":\"Midnight Rain\",\"publish_date\":\"2000-12-16\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"After the collapse of a nanotechnology      society in England, the young survivors lay the      foundation for a new society.\",\"id\":\"bk103\",\"title\":\"Maeve Ascendant\",\"publish_date\":\"2000-11-17\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"In post-apocalypse England, the mysterious      agent known only as Oberon helps to create a new life      for the inhabitants of London. Sequel to Maeve      Ascendant.\",\"id\":\"bk104\",\"title\":\"Oberon's Legacy\",\"publish_date\":\"2001-03-10\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"The two daughters of Maeve, half-sisters,      battle one another for control of England. Sequel to      Oberon's Legacy.\",\"id\":\"bk105\",\"title\":\"The Sundered Grail\",\"publish_date\":\"2001-09-10\"},{\"json\":\"object\"},{\"author\":\"Thurman, Paula\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"A deep sea diver finds true love twenty      thousand leagues beneath the sea.\",\"id\":\"bk107\",\"title\":\"Splish Splash\",\"publish_date\":\"2000-11-02\"},{\"author\":\"Knorr, Stefan\",\"price\":4.95,\"genre\":\"Horror\",\"description\":\"An anthology of horror stories about roaches,      centipedes, scorpions  and other insects.\",\"id\":\"bk108\",\"title\":\"Creepy Crawlies\",\"publish_date\":\"2000-12-06\"},{\"author\":\"Kress, Peter\",\"price\":6.95,\"genre\":\"Science Fiction\",\"description\":\"After an inadvertant trip through a Heisenberg      Uncertainty Device, James Salway discovers the problems      of being quantum.\",\"id\":\"bk109\",\"title\":\"Paradox Lost\",\"publish_date\":\"2000-11-02\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"Microsoft's .NET initiative is explored in      detail in this deep programmer's reference.\",\"id\":\"bk110\",\"title\":\"Microsoft .NET: The Programming Bible\",\"publish_date\":\"2000-12-09\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"The Microsoft MSXML3 parser is covered in      detail, with attention to XML DOM interfaces, XSLT processing,      SAX and more.\",\"id\":\"bk111\",\"title\":\"MSXML3: A Comprehensive Guide\",\"publish_date\":\"2000-12-01\"},{\"author\":\"Galos, Mike\",\"price\":49.95,\"genre\":\"Computer\",\"description\":\"Microsoft Visual Studio 7 is explored in depth,      looking at how Visual Basic, Visual C++, C#, and ASP+ are      integrated into a comprehensive development      environment.\",\"id\":\"bk112\",\"title\":\"Visual Studio 7: A Comprehensive Guide\",\"publish_date\":\"2001-04-16\"}]}}");
            File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
                		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
                		+ "      an evil sorceress, and her own childhood to become queen"
                		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
                		+ "      society in England, the young survivors lay the"
                		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
                		+ "      agent known only as Oberon helps to create a new life"
                		+ "      for the inhabitants of London. Sequel to Maeve"
                		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
                		+ "      battle one another for control of England. Sequel to"
                		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
                		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
                		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
                		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
                		+ "      Uncertainty Device, James Salway discovers the problems"
                		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
                		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
                		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
                		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
                		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
                		+ "      integrated into a comprehensive development"
                		+ "      environment.</description></book></catalog>");
            } finally {
                fileWriter.close();
            }

            Reader reader = new FileReader(tempFile);
            try {
            	
                JSONObject jsonObject = XML.toJSONObject(reader, new JSONPointer("/catalog/book/5"), new JSONObject("{\"json\":\"object\"}"));
                Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            fail("Error: " +e.getMessage());
        }
    }
    @Test 
    public void testJsonObjectReplaceFail() {
    	try {
            JSONObject expectedJsonObject = new JSONObject("{\"catalog\":{\"book\":[{\"author\":\"Gambardella, Matthew\",\"price\":44.95,\"genre\":\"Computer\",\"description\":\"An in-depth look at creating applications      with XML.\",\"id\":\"bk101\",\"title\":\"XML Developer's Guide\",\"publish_date\":\"2000-10-01\"},{\"author\":\"Ralls, Kim\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"A former architect battles corporate zombies,      an evil sorceress, and her own childhood to become queen      of the world.\",\"id\":\"bk102\",\"title\":\"Midnight Rain\",\"publish_date\":\"2000-12-16\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"After the collapse of a nanotechnology      society in England, the young survivors lay the      foundation for a new society.\",\"id\":\"bk103\",\"title\":\"Maeve Ascendant\",\"publish_date\":\"2000-11-17\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"In post-apocalypse England, the mysterious      agent known only as Oberon helps to create a new life      for the inhabitants of London. Sequel to Maeve      Ascendant.\",\"id\":\"bk104\",\"title\":\"Oberon's Legacy\",\"publish_date\":\"2001-03-10\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"The two daughters of Maeve, half-sisters,      battle one another for control of England. Sequel to      Oberon's Legacy.\",\"id\":\"bk105\",\"title\":\"The Sundered Grail\",\"publish_date\":\"2001-09-10\"},{\"json\":\"object\"},{\"author\":\"Thurman, Paula\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"A deep sea diver finds true love twenty      thousand leagues beneath the sea.\",\"id\":\"bk107\",\"title\":\"Splish Splash\",\"publish_date\":\"2000-11-02\"},{\"author\":\"Knorr, Stefan\",\"price\":4.95,\"genre\":\"Horror\",\"description\":\"An anthology of horror stories about roaches,      centipedes, scorpions  and other insects.\",\"id\":\"bk108\",\"title\":\"Creepy Crawlies\",\"publish_date\":\"2000-12-06\"},{\"author\":\"Kress, Peter\",\"price\":6.95,\"genre\":\"Science Fiction\",\"description\":\"After an inadvertant trip through a Heisenberg      Uncertainty Device, James Salway discovers the problems      of being quantum.\",\"id\":\"bk109\",\"title\":\"Paradox Lost\",\"publish_date\":\"2000-11-02\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"Microsoft's .NET initiative is explored in      detail in this deep programmer's reference.\",\"id\":\"bk110\",\"title\":\"Microsoft .NET: The Programming Bible\",\"publish_date\":\"2000-12-09\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"The Microsoft MSXML3 parser is covered in      detail, with attention to XML DOM interfaces, XSLT processing,      SAX and more.\",\"id\":\"bk111\",\"title\":\"MSXML3: A Comprehensive Guide\",\"publish_date\":\"2000-12-01\"},{\"author\":\"Galos, Mike\",\"price\":49.95,\"genre\":\"Computer\",\"description\":\"Microsoft Visual Studio 7 is explored in depth,      looking at how Visual Basic, Visual C++, C#, and ASP+ are      integrated into a comprehensive development      environment.\",\"id\":\"bk112\",\"title\":\"Visual Studio 7: A Comprehensive Guide\",\"publish_date\":\"2001-04-16\"}]}}");
            File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
                		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
                		+ "      an evil sorceress, and her own childhood to become queen"
                		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
                		+ "      society in England, the young survivors lay the"
                		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
                		+ "      agent known only as Oberon helps to create a new life"
                		+ "      for the inhabitants of London. Sequel to Maeve"
                		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
                		+ "      battle one another for control of England. Sequel to"
                		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
                		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
                		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
                		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
                		+ "      Uncertainty Device, James Salway discovers the problems"
                		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
                		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
                		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
                		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
                		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
                		+ "      integrated into a comprehensive development"
                		+ "      environment.</description></book></catalog>");
            } finally {
                fileWriter.close();
            }

            Reader reader = new FileReader(tempFile);
            try {
            	
                JSONObject jsonObject = XML.toJSONObject(reader, new JSONPointer("/catalog/book/5"), new JSONObject("{\"json\":\"object\"}"));
//                Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
                assertNotEquals(jsonObject, expectedJsonObject);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            fail("Error: " +e.getMessage());
        }
    }
    @Test(expected=JSONPointerException.class)
    public void testJsonObjectReplaceThrowsJSONPointerException() {
    	try {
            JSONObject expectedJsonObject = new JSONObject("{\"catalog\":{\"book\":[{\"author\":\"Gambardella, Matthew\",\"price\":44.95,\"genre\":\"Computer\",\"description\":\"An in-depth look at creating applications      with XML.\",\"id\":\"bk101\",\"title\":\"XML Developer's Guide\",\"publish_date\":\"2000-10-01\"},{\"author\":\"Ralls, Kim\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"A former architect battles corporate zombies,      an evil sorceress, and her own childhood to become queen      of the world.\",\"id\":\"bk102\",\"title\":\"Midnight Rain\",\"publish_date\":\"2000-12-16\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"After the collapse of a nanotechnology      society in England, the young survivors lay the      foundation for a new society.\",\"id\":\"bk103\",\"title\":\"Maeve Ascendant\",\"publish_date\":\"2000-11-17\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"In post-apocalypse England, the mysterious      agent known only as Oberon helps to create a new life      for the inhabitants of London. Sequel to Maeve      Ascendant.\",\"id\":\"bk104\",\"title\":\"Oberon's Legacy\",\"publish_date\":\"2001-03-10\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"The two daughters of Maeve, half-sisters,      battle one another for control of England. Sequel to      Oberon's Legacy.\",\"id\":\"bk105\",\"title\":\"The Sundered Grail\",\"publish_date\":\"2001-09-10\"},{\"json\":\"object\"},{\"author\":\"Thurman, Paula\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"A deep sea diver finds true love twenty      thousand leagues beneath the sea.\",\"id\":\"bk107\",\"title\":\"Splish Splash\",\"publish_date\":\"2000-11-02\"},{\"author\":\"Knorr, Stefan\",\"price\":4.95,\"genre\":\"Horror\",\"description\":\"An anthology of horror stories about roaches,      centipedes, scorpions  and other insects.\",\"id\":\"bk108\",\"title\":\"Creepy Crawlies\",\"publish_date\":\"2000-12-06\"},{\"author\":\"Kress, Peter\",\"price\":6.95,\"genre\":\"Science Fiction\",\"description\":\"After an inadvertant trip through a Heisenberg      Uncertainty Device, James Salway discovers the problems      of being quantum.\",\"id\":\"bk109\",\"title\":\"Paradox Lost\",\"publish_date\":\"2000-11-02\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"Microsoft's .NET initiative is explored in      detail in this deep programmer's reference.\",\"id\":\"bk110\",\"title\":\"Microsoft .NET: The Programming Bible\",\"publish_date\":\"2000-12-09\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"The Microsoft MSXML3 parser is covered in      detail, with attention to XML DOM interfaces, XSLT processing,      SAX and more.\",\"id\":\"bk111\",\"title\":\"MSXML3: A Comprehensive Guide\",\"publish_date\":\"2000-12-01\"},{\"author\":\"Galos, Mike\",\"price\":49.95,\"genre\":\"Computer\",\"description\":\"Microsoft Visual Studio 7 is explored in depth,      looking at how Visual Basic, Visual C++, C#, and ASP+ are      integrated into a comprehensive development      environment.\",\"id\":\"bk112\",\"title\":\"Visual Studio 7: A Comprehensive Guide\",\"publish_date\":\"2001-04-16\"}]}}");
            File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
                		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
                		+ "      an evil sorceress, and her own childhood to become queen"
                		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
                		+ "      society in England, the young survivors lay the"
                		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
                		+ "      agent known only as Oberon helps to create a new life"
                		+ "      for the inhabitants of London. Sequel to Maeve"
                		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
                		+ "      battle one another for control of England. Sequel to"
                		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
                		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
                		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
                		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
                		+ "      Uncertainty Device, James Salway discovers the problems"
                		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
                		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
                		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
                		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
                		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
                		+ "      integrated into a comprehensive development"
                		+ "      environment.</description></book></catalog>");
            } finally {
                fileWriter.close();
            }

            Reader reader = new FileReader(tempFile);
            try {
            	
                XML.toJSONObject(reader, new JSONPointer("/catalog/book/200"), new JSONObject("{\"json\":\"object\"}"));
//                Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
//                assertNotEquals(jsonObject, expectedJsonObject);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            fail("Error: " +e.getMessage());
        }
    }
    
    @Test
    public void testConvertingKeysWhileParsingAppendingKey() {
    	try {
	    	JSONObject expectedJsonObject = new JSONObject("{\"swe262_catalog\":{\"swe262_book\":[{\"swe262_price\":44.95,\"swe262_genre\":\"Computer\",\"swe262_author\":\"Gambardella, Matthew\",\"swe262_publish_date\":\"2000-10-01\",\"swe262_description\":\"An in-depth look at creating applications      with XML.\",\"swe262_title\":\"XML Developer's Guide\",\"swe262_id\":\"bk101\"},{\"swe262_price\":5.95,\"swe262_genre\":\"Fantasy\",\"swe262_author\":\"Ralls, Kim\",\"swe262_publish_date\":\"2000-12-16\",\"swe262_description\":\"A former architect battles corporate zombies,      an evil sorceress, and her own childhood to become queen      of the world.\",\"swe262_title\":\"Midnight Rain\",\"swe262_id\":\"bk102\"},{\"swe262_price\":5.95,\"swe262_genre\":\"Fantasy\",\"swe262_author\":\"Corets, Eva\",\"swe262_publish_date\":\"2000-11-17\",\"swe262_description\":\"After the collapse of a nanotechnology      society in England, the young survivors lay the      foundation for a new society.\",\"swe262_title\":\"Maeve Ascendant\",\"swe262_id\":\"bk103\"},{\"swe262_price\":5.95,\"swe262_genre\":\"Fantasy\",\"swe262_author\":\"Corets, Eva\",\"swe262_publish_date\":\"2001-03-10\",\"swe262_description\":\"In post-apocalypse England, the mysterious      agent known only as Oberon helps to create a new life      for the inhabitants of London. Sequel to Maeve      Ascendant.\",\"swe262_title\":\"Oberon's Legacy\",\"swe262_id\":\"bk104\"},{\"swe262_price\":5.95,\"swe262_genre\":\"Fantasy\",\"swe262_author\":\"Corets, Eva\",\"swe262_publish_date\":\"2001-09-10\",\"swe262_description\":\"The two daughters of Maeve, half-sisters,      battle one another for control of England. Sequel to      Oberon's Legacy.\",\"swe262_title\":\"The Sundered Grail\",\"swe262_id\":\"bk105\"},{\"swe262_price\":4.95,\"swe262_genre\":\"Romance\",\"swe262_author\":\"Randall, Cynthia\",\"swe262_publish_date\":\"2000-09-02\",\"swe262_description\":\"When Carla meets Paul at an ornithology      conference, tempers fly as feathers get ruffled.\",\"swe262_title\":\"Lover Birds\",\"swe262_id\":\"bk106\"},{\"swe262_price\":4.95,\"swe262_genre\":\"Romance\",\"swe262_author\":\"Thurman, Paula\",\"swe262_publish_date\":\"2000-11-02\",\"swe262_description\":\"A deep sea diver finds true love twenty      thousand leagues beneath the sea.\",\"swe262_title\":\"Splish Splash\",\"swe262_id\":\"bk107\"},{\"swe262_price\":4.95,\"swe262_genre\":\"Horror\",\"swe262_author\":\"Knorr, Stefan\",\"swe262_publish_date\":\"2000-12-06\",\"swe262_description\":\"An anthology of horror stories about roaches,      centipedes, scorpions  and other insects.\",\"swe262_title\":\"Creepy Crawlies\",\"swe262_id\":\"bk108\"},{\"swe262_price\":6.95,\"swe262_genre\":\"Science Fiction\",\"swe262_author\":\"Kress, Peter\",\"swe262_publish_date\":\"2000-11-02\",\"swe262_description\":\"After an inadvertant trip through a Heisenberg      Uncertainty Device, James Salway discovers the problems      of being quantum.\",\"swe262_title\":\"Paradox Lost\",\"swe262_id\":\"bk109\"},{\"swe262_price\":36.95,\"swe262_genre\":\"Computer\",\"swe262_author\":\"O'Brien, Tim\",\"swe262_publish_date\":\"2000-12-09\",\"swe262_description\":\"Microsoft's .NET initiative is explored in      detail in this deep programmer's reference.\",\"swe262_title\":\"Microsoft .NET: The Programming Bible\",\"swe262_id\":\"bk110\"},{\"swe262_price\":36.95,\"swe262_genre\":\"Computer\",\"swe262_author\":\"O'Brien, Tim\",\"swe262_publish_date\":\"2000-12-01\",\"swe262_description\":\"The Microsoft MSXML3 parser is covered in      detail, with attention to XML DOM interfaces, XSLT processing,      SAX and more.\",\"swe262_title\":\"MSXML3: A Comprehensive Guide\",\"swe262_id\":\"bk111\"},{\"swe262_price\":49.95,\"swe262_genre\":\"Computer\",\"swe262_author\":\"Galos, Mike\",\"swe262_publish_date\":\"2001-04-16\",\"swe262_description\":\"Microsoft Visual Studio 7 is explored in depth,      looking at how Visual Basic, Visual C++, C#, and ASP+ are      integrated into a comprehensive development      environment.\",\"swe262_title\":\"Visual Studio 7: A Comprehensive Guide\",\"swe262_id\":\"bk112\"}]}}");
	        File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
	        FileWriter fileWriter = new FileWriter(tempFile);
	        try {
	            fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
	            		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
	            		+ "      an evil sorceress, and her own childhood to become queen"
	            		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
	            		+ "      society in England, the young survivors lay the"
	            		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
	            		+ "      agent known only as Oberon helps to create a new life"
	            		+ "      for the inhabitants of London. Sequel to Maeve"
	            		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
	            		+ "      battle one another for control of England. Sequel to"
	            		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
	            		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
	            		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
	            		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
	            		+ "      Uncertainty Device, James Salway discovers the problems"
	            		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
	            		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
	            		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
	            		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
	            		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
	            		+ "      integrated into a comprehensive development"
	            		+ "      environment.</description></book></catalog>");
	        } finally {
	            fileWriter.close();
	        }
	
	        Reader reader = new FileReader(tempFile);
	        try {
	        	Function<String, String> keyConverter = new Function<String, String>() {
					@Override
					public String apply(String key) { return "swe262_"+key; }
	        	};
				
				JSONObject jsonObject = XML.toJSONObject(reader, keyConverter);
		        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
			} finally {
				reader.close();
	        }
	    } catch (IOException e) {
	        fail("Error: " +e.getMessage());
	    }
    }
    @Test
    public void testConvertingKeysWhileParsingReverseKeyTransformer() {
    	try {
	    	JSONObject expectedJsonObject = new JSONObject("{\"golatac\":{\"koob\":[{\"rohtua\":\"Gambardella, Matthew\",\"ecirp\":44.95,\"di\":\"bk101\",\"erneg\":\"Computer\",\"etad_hsilbup\":\"2000-10-01\",\"eltit\":\"XML Developer's Guide\",\"noitpircsed\":\"An in-depth look at creating applications      with XML.\"},{\"rohtua\":\"Ralls, Kim\",\"ecirp\":5.95,\"di\":\"bk102\",\"erneg\":\"Fantasy\",\"etad_hsilbup\":\"2000-12-16\",\"eltit\":\"Midnight Rain\",\"noitpircsed\":\"A former architect battles corporate zombies,      an evil sorceress, and her own childhood to become queen      of the world.\"},{\"rohtua\":\"Corets, Eva\",\"ecirp\":5.95,\"di\":\"bk103\",\"erneg\":\"Fantasy\",\"etad_hsilbup\":\"2000-11-17\",\"eltit\":\"Maeve Ascendant\",\"noitpircsed\":\"After the collapse of a nanotechnology      society in England, the young survivors lay the      foundation for a new society.\"},{\"rohtua\":\"Corets, Eva\",\"ecirp\":5.95,\"di\":\"bk104\",\"erneg\":\"Fantasy\",\"etad_hsilbup\":\"2001-03-10\",\"eltit\":\"Oberon's Legacy\",\"noitpircsed\":\"In post-apocalypse England, the mysterious      agent known only as Oberon helps to create a new life      for the inhabitants of London. Sequel to Maeve      Ascendant.\"},{\"rohtua\":\"Corets, Eva\",\"ecirp\":5.95,\"di\":\"bk105\",\"erneg\":\"Fantasy\",\"etad_hsilbup\":\"2001-09-10\",\"eltit\":\"The Sundered Grail\",\"noitpircsed\":\"The two daughters of Maeve, half-sisters,      battle one another for control of England. Sequel to      Oberon's Legacy.\"},{\"rohtua\":\"Randall, Cynthia\",\"ecirp\":4.95,\"di\":\"bk106\",\"erneg\":\"Romance\",\"etad_hsilbup\":\"2000-09-02\",\"eltit\":\"Lover Birds\",\"noitpircsed\":\"When Carla meets Paul at an ornithology      conference, tempers fly as feathers get ruffled.\"},{\"rohtua\":\"Thurman, Paula\",\"ecirp\":4.95,\"di\":\"bk107\",\"erneg\":\"Romance\",\"etad_hsilbup\":\"2000-11-02\",\"eltit\":\"Splish Splash\",\"noitpircsed\":\"A deep sea diver finds true love twenty      thousand leagues beneath the sea.\"},{\"rohtua\":\"Knorr, Stefan\",\"ecirp\":4.95,\"di\":\"bk108\",\"erneg\":\"Horror\",\"etad_hsilbup\":\"2000-12-06\",\"eltit\":\"Creepy Crawlies\",\"noitpircsed\":\"An anthology of horror stories about roaches,      centipedes, scorpions  and other insects.\"},{\"rohtua\":\"Kress, Peter\",\"ecirp\":6.95,\"di\":\"bk109\",\"erneg\":\"Science Fiction\",\"etad_hsilbup\":\"2000-11-02\",\"eltit\":\"Paradox Lost\",\"noitpircsed\":\"After an inadvertant trip through a Heisenberg      Uncertainty Device, James Salway discovers the problems      of being quantum.\"},{\"rohtua\":\"O'Brien, Tim\",\"ecirp\":36.95,\"di\":\"bk110\",\"erneg\":\"Computer\",\"etad_hsilbup\":\"2000-12-09\",\"eltit\":\"Microsoft .NET: The Programming Bible\",\"noitpircsed\":\"Microsoft's .NET initiative is explored in      detail in this deep programmer's reference.\"},{\"rohtua\":\"O'Brien, Tim\",\"ecirp\":36.95,\"di\":\"bk111\",\"erneg\":\"Computer\",\"etad_hsilbup\":\"2000-12-01\",\"eltit\":\"MSXML3: A Comprehensive Guide\",\"noitpircsed\":\"The Microsoft MSXML3 parser is covered in      detail, with attention to XML DOM interfaces, XSLT processing,      SAX and more.\"},{\"rohtua\":\"Galos, Mike\",\"ecirp\":49.95,\"di\":\"bk112\",\"erneg\":\"Computer\",\"etad_hsilbup\":\"2001-04-16\",\"eltit\":\"Visual Studio 7: A Comprehensive Guide\",\"noitpircsed\":\"Microsoft Visual Studio 7 is explored in depth,      looking at how Visual Basic, Visual C++, C#, and ASP+ are      integrated into a comprehensive development      environment.\"}]}}");
	        File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
	        FileWriter fileWriter = new FileWriter(tempFile);
	        try {
	            fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
	            		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
	            		+ "      an evil sorceress, and her own childhood to become queen"
	            		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
	            		+ "      society in England, the young survivors lay the"
	            		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
	            		+ "      agent known only as Oberon helps to create a new life"
	            		+ "      for the inhabitants of London. Sequel to Maeve"
	            		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
	            		+ "      battle one another for control of England. Sequel to"
	            		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
	            		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
	            		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
	            		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
	            		+ "      Uncertainty Device, James Salway discovers the problems"
	            		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
	            		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
	            		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
	            		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
	            		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
	            		+ "      integrated into a comprehensive development"
	            		+ "      environment.</description></book></catalog>");
	        } finally {
	            fileWriter.close();
	        }
	
	        Reader reader = new FileReader(tempFile);
	        try {
	        	Function<String, String> keyConverter = new Function<String, String>() {
					@Override
					public String apply(String key) { return new StringBuilder().append(key).reverse().toString(); }
	        	};
				
				JSONObject jsonObject = XML.toJSONObject(reader, keyConverter);
		        Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
			} finally {
				reader.close();
	        }
	    } catch (IOException e) {
	        fail("Error: " +e.getMessage());
	    }
    }
    
    @Test
    public void testConvertingKeysWhileParsingFail() {
    	try {
            JSONObject expectedJsonObject = new JSONObject("{\"author\":\"Randall, Cynthia\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"When Carla meets Paul at an ornithology\\n      conference, tempers fly as feathers get ruffled.\",\"id\":\"bk106\",\"title\":\"Lover Birds\",\"publish_date\":\"2000-09-02\"}");
	        File tempFile = this.testFolder.newFile("testExtractJSONObject.xml");
	        FileWriter fileWriter = new FileWriter(tempFile);
	        try {
	            fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications"
	            		+ "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,"
	            		+ "      an evil sorceress, and her own childhood to become queen"
	            		+ "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology"
	            		+ "      society in England, the young survivors lay the"
	            		+ "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious"
	            		+ "      agent known only as Oberon helps to create a new life"
	            		+ "      for the inhabitants of London. Sequel to Maeve"
	            		+ "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,"
	            		+ "      battle one another for control of England. Sequel to"
	            		+ "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology"
	            		+ "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty"
	            		+ "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,"
	            		+ "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg"
	            		+ "      Uncertainty Device, James Salway discovers the problems"
	            		+ "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in"
	            		+ "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in"
	            		+ "      detail, with attention to XML DOM interfaces, XSLT processing,"
	            		+ "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,"
	            		+ "      looking at how Visual Basic, Visual C++, C#, and ASP+ are"
	            		+ "      integrated into a comprehensive development"
	            		+ "      environment.</description></book></catalog>");
	        } finally {
	            fileWriter.close();
	        }
	
	        Reader reader = new FileReader(tempFile);
	        try {
	        	Function<String, String> keyConverter = new Function<String, String>() {
					@Override
					public String apply(String key) { return "swe262_"+key; }
	        	};
				
				JSONObject jsonObject = XML.toJSONObject(reader, keyConverter);
	        // Util.compareActualVsExpectedJsonObjects(jsonObject,expectedJsonObject);
				assertNotEquals(jsonObject, expectedJsonObject);
			} finally {
				reader.close();
	        }
	    } catch (IOException e) {
	        fail("Error: " +e.getMessage());
	    }
    }

    @Test
    public void testAsyncParseJSONObject() {
        try {
            File tempFile = this.testFolder.newFile("testAsyncParseJSONObject.xml");
            File tempFile1 = this.testFolder.newFile("testAsyncParseJSONObject.json");

            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write("<?xml version=\"1.0\"?><catalog><book id=\"bk101\"><author>Gambardella, Matthew</author><title>XML Developer's Guide</title><genre>Computer</genre><price>44.95</price><publish_date>2000-10-01</publish_date><description>An in-depth look at creating applications\n"
                        + "      with XML.</description></book><book id=\"bk102\"><author>Ralls, Kim</author><title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-12-16</publish_date><description>A former architect battles corporate zombies,\n"
                        + "      an evil sorceress, and her own childhood to become queen\n"
                        + "      of the world.</description></book><book id=\"bk103\"><author>Corets, Eva</author><title>Maeve Ascendant</title><genre>Fantasy</genre><price>5.95</price><publish_date>2000-11-17</publish_date><description>After the collapse of a nanotechnology\n"
                        + "      society in England, the young survivors lay the\n"
                        + "      foundation for a new society.</description></book><book id=\"bk104\"><author>Corets, Eva</author><title>Oberon's Legacy</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-03-10</publish_date><description>In post-apocalypse England, the mysterious\n"
                        + "      agent known only as Oberon helps to create a new life\n"
                        + "      for the inhabitants of London. Sequel to Maeve\n"
                        + "      Ascendant.</description></book><book id=\"bk105\"><author>Corets, Eva</author><title>The Sundered Grail</title><genre>Fantasy</genre><price>5.95</price><publish_date>2001-09-10</publish_date><description>The two daughters of Maeve, half-sisters,\n"
                        + "      battle one another for control of England. Sequel to\n"
                        + "      Oberon's Legacy.</description></book><book id=\"bk106\"><author>Randall, Cynthia</author><title>Lover Birds</title><genre>Romance</genre><price>4.95</price><publish_date>2000-09-02</publish_date><description>When Carla meets Paul at an ornithology\n"
                        + "      conference, tempers fly as feathers get ruffled.</description></book><book id=\"bk107\"><author>Thurman, Paula</author><title>Splish Splash</title><genre>Romance</genre><price>4.95</price><publish_date>2000-11-02</publish_date><description>A deep sea diver finds true love twenty\n"
                        + "      thousand leagues beneath the sea.</description></book><book id=\"bk108\"><author>Knorr, Stefan</author><title>Creepy Crawlies</title><genre>Horror</genre><price>4.95</price><publish_date>2000-12-06</publish_date><description>An anthology of horror stories about roaches,\n"
                        + "      centipedes, scorpions  and other insects.</description></book><book id=\"bk109\"><author>Kress, Peter</author><title>Paradox Lost</title><genre>Science Fiction</genre><price>6.95</price><publish_date>2000-11-02</publish_date><description>After an inadvertant trip through a Heisenberg\n"
                        + "      Uncertainty Device, James Salway discovers the problems\n"
                        + "      of being quantum.</description></book><book id=\"bk110\"><author>O'Brien, Tim</author><title>Microsoft .NET: The Programming Bible</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-09</publish_date><description>Microsoft's .NET initiative is explored in\n"
                        + "      detail in this deep programmer's reference.</description></book><book id=\"bk111\"><author>O'Brien, Tim</author><title>MSXML3: A Comprehensive Guide</title><genre>Computer</genre><price>36.95</price><publish_date>2000-12-01</publish_date><description>The Microsoft MSXML3 parser is covered in\n"
                        + "      detail, with attention to XML DOM interfaces, XSLT processing,\n"
                        + "      SAX and more.</description></book><book id=\"bk112\"><author>Galos, Mike</author><title>Visual Studio 7: A Comprehensive Guide</title><genre>Computer</genre><price>49.95</price><publish_date>2001-04-16</publish_date><description>Microsoft Visual Studio 7 is explored in depth,\n"
                        + "      looking at how Visual Basic, Visual C++, C#, and ASP+ are\n"
                        + "      integrated into a comprehensive development\n"
                        + "      environment.</description></book></catalog>\n"
                        + "");
            } finally {
                fileWriter.close();
            }
            FileWriter newFileWriter = new FileWriter(tempFile1);
            Reader reader = new FileReader(tempFile);

            try {
                XML.toJSONObject(reader, (JSONObject jo) -> {
                    jo.write(newFileWriter);
                    System.out.println(jo.toString());
                    try {
                        newFileWriter.close();
                        JSONObject expectedJsonObject = new JSONObject("{\"catalog\":{\"book\":[{\"author\":\"Gambardella, Matthew\",\"price\":44.95,\"genre\":\"Computer\",\"description\":\"An in-depth look at creating applications\\n      with XML.\",\"id\":\"bk101\",\"title\":\"XML Developer's Guide\",\"publish_date\":\"2000-10-01\"},{\"author\":\"Ralls, Kim\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"A former architect battles corporate zombies,\\n      an evil sorceress, and her own childhood to become queen\\n      of the world.\",\"id\":\"bk102\",\"title\":\"Midnight Rain\",\"publish_date\":\"2000-12-16\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"After the collapse of a nanotechnology\\n      society in England, the young survivors lay the\\n      foundation for a new society.\",\"id\":\"bk103\",\"title\":\"Maeve Ascendant\",\"publish_date\":\"2000-11-17\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"In post-apocalypse England, the mysterious\\n      agent known only as Oberon helps to create a new life\\n      for the inhabitants of London. Sequel to Maeve\\n      Ascendant.\",\"id\":\"bk104\",\"title\":\"Oberon's Legacy\",\"publish_date\":\"2001-03-10\"},{\"author\":\"Corets, Eva\",\"price\":5.95,\"genre\":\"Fantasy\",\"description\":\"The two daughters of Maeve, half-sisters,\\n      battle one another for control of England. Sequel to\\n      Oberon's Legacy.\",\"id\":\"bk105\",\"title\":\"The Sundered Grail\",\"publish_date\":\"2001-09-10\"},{\"author\":\"Randall, Cynthia\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"When Carla meets Paul at an ornithology\\n      conference, tempers fly as feathers get ruffled.\",\"id\":\"bk106\",\"title\":\"Lover Birds\",\"publish_date\":\"2000-09-02\"},{\"author\":\"Thurman, Paula\",\"price\":4.95,\"genre\":\"Romance\",\"description\":\"A deep sea diver finds true love twenty\\n      thousand leagues beneath the sea.\",\"id\":\"bk107\",\"title\":\"Splish Splash\",\"publish_date\":\"2000-11-02\"},{\"author\":\"Knorr, Stefan\",\"price\":4.95,\"genre\":\"Horror\",\"description\":\"An anthology of horror stories about roaches,\\n      centipedes, scorpions  and other insects.\",\"id\":\"bk108\",\"title\":\"Creepy Crawlies\",\"publish_date\":\"2000-12-06\"},{\"author\":\"Kress, Peter\",\"price\":6.95,\"genre\":\"Science Fiction\",\"description\":\"After an inadvertant trip through a Heisenberg\\n      Uncertainty Device, James Salway discovers the problems\\n      of being quantum.\",\"id\":\"bk109\",\"title\":\"Paradox Lost\",\"publish_date\":\"2000-11-02\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"Microsoft's .NET initiative is explored in\\n      detail in this deep programmer's reference.\",\"id\":\"bk110\",\"title\":\"Microsoft .NET: The Programming Bible\",\"publish_date\":\"2000-12-09\"},{\"author\":\"O'Brien, Tim\",\"price\":36.95,\"genre\":\"Computer\",\"description\":\"The Microsoft MSXML3 parser is covered in\\n      detail, with attention to XML DOM interfaces, XSLT processing,\\n      SAX and more.\",\"id\":\"bk111\",\"title\":\"MSXML3: A Comprehensive Guide\",\"publish_date\":\"2000-12-01\"},{\"author\":\"Galos, Mike\",\"price\":49.95,\"genre\":\"Computer\",\"description\":\"Microsoft Visual Studio 7 is explored in depth,\\n      looking at how Visual Basic, Visual C++, C#, and ASP+ are\\n      integrated into a comprehensive development\\n      environment.\",\"id\":\"bk112\",\"title\":\"Visual Studio 7: A Comprehensive Guide\",\"publish_date\":\"2001-04-16\"}]}}\n");
                        JSONObject newParsedJSONObject = new JSONObject(Files.lines(Paths.get(tempFile1.getAbsolutePath())).collect(Collectors.joining()));
                        Util.compareActualVsExpectedJsonObjects(newParsedJSONObject, expectedJsonObject);
                        Util.compareActualVsExpectedJsonObjects(jo, expectedJsonObject);
                    }
                    catch (Exception e) {
                        fail("Error: "+e.getMessage());
                    }
                }, (Exception e) -> fail("Error: " +e.getMessage()));


            } finally {
                reader.close();
            }
        } catch (Exception e) {
            fail("Error: " +e.getMessage());
        }
    }

    @Test(expected=AssertionError.class)
    public void testAsyncParseThrowsException() {
        try {
            File tempFile = this.testFolder.newFile("testAsyncParseJSONObject.xml");

            FileWriter fileWriter = new FileWriter(tempFile);
            try {
                fileWriter.write("<?xml version=\"1.0\"?></catalog>\n");
            } finally {
                fileWriter.close();
            }
            Reader reader = new FileReader(tempFile);

            try {
                XML.toJSONObject(reader, (JSONObject jo) -> { }, (Exception e) -> fail("Error: " +e.getMessage()));
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            fail("Error: " +e.getMessage());
        }
    }
}
