package org.platformlayer;

import javax.xml.bind.JAXBException;

import org.platformlayer.common.UntypedItem;
import org.platformlayer.core.model.ManagedItemState;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Tags;
import org.platformlayer.xml.JaxbHelper;
import org.platformlayer.xml.XmlHelper;
import org.platformlayer.xml.XmlHelper.ElementInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.common.base.Strings;

public class UntypedItemXml implements UntypedItem {

	private final Element root;
	private Tags tags;

	private PlatformLayerKey platformLayerKey;

	public UntypedItemXml(Element root) {
		this.root = root;
	}

	public static UntypedItemXml build(String xml) {
		Element documentElement;

		try {
			Document dom = XmlHelper.parseXmlDocument(xml, true);
			documentElement = dom.getDocumentElement();
		} catch (Exception e) {
			throw new IllegalArgumentException("Error parsing XML", e);
		}

		return new UntypedItemXml(documentElement);
	}

	public Element getDataElement() {
		return root;

		// String xml = o.getModelData();
		//
		// Element documentElement;
		//
		// try {
		// Document dom = XmlHelper.parseXmlDocument(xml, false);
		// documentElement = dom.getDocumentElement();
		// } catch (Exception e) {
		// throw new IllegalArgumentException("Error parsing XML", e);
		// }
	}

	// public Object getId() {
	// }
	//
	// public Object getState() {
	// }

	@Override
	public Tags getTags() {
		if (tags == null) {
			Node tagsElement = XmlHelper.findUniqueChild(root, "tags", false);
			if (tagsElement == null) {
				return null;
			}

			JaxbHelper helper = JaxbHelper.get(Tags.class);
			try {
				tags = (Tags) helper.unmarshal(tagsElement);
			} catch (JAXBException e) {
				throw new IllegalStateException("Error parsing tags data", e);
			}
		}
		return tags;
	}

	public void setTags(Tags tags) {
		Document tagsDocument;

		JaxbHelper helper = JaxbHelper.get(Tags.class);
		try {
			tagsDocument = helper.marshalToDom(tags);
		} catch (JAXBException e) {
			throw new IllegalStateException("Error parsing tags data", e);
		}

		String xml = XmlHelper.safeToXml(tagsDocument.getDocumentElement());

		Node newTags = tagsDocument.getDocumentElement();// XmlHelper.findUniqueChild(tagsDocument.getDocumentElement(),
															// "tags");
		Node imported = root.getOwnerDocument().adoptNode(newTags);

		Node existing = XmlHelper.findUniqueChild(root, "tags", false);
		if (existing == null) {
			root.appendChild(imported);
		} else {
			root.replaceChild(imported, existing);
		}

		xml = XmlHelper.safeToXml(root);

		// To avoid any possible state problems, we set to null rather than copying
		this.tags = null;
	}

	// public String getId() {
	// Node idElement = findIdElement();
	// if (idElement == null)
	// return null;
	// return idElement.getTextContent();
	// }
	//
	// public void setId(String id) {
	// Node idElement = findIdElement();
	// if (idElement == null)
	// throw new IllegalStateException();
	// idElement.setTextContent(id);
	// }

	// private Node findIdElement() {
	// Node idElement = XmlHelper.findUniqueChild(root, "id");
	// return idElement;
	// }

	private Node findKeyElement(boolean create) {
		Node element = XmlHelper.findUniqueChild(root, "key", create);
		return element;
	}

	@Override
	public ManagedItemState getState() {
		Node element = XmlHelper.findUniqueChild(root, "state", false);
		if (element == null) {
			return null;
		}
		String state = element.getTextContent();
		return ManagedItemState.valueOf(state);
	}

	public ElementInfo getRootElementInfo() {
		String name = null;
		String namespace = null;

		// xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns16:networkConnection"
		String xsiNs = "http://www.w3.org/2001/XMLSchema-instance";
		String xsiType = root.getAttributeNS(xsiNs, "type");
		if (xsiType != null) {
			// String xsiType = xsiTypeNode.getValue();
			String[] tokens = xsiType.split(":");
			if (tokens.length == 1) {
				namespace = null;
				name = tokens[0];
			} else if (tokens.length == 2) {
				name = tokens[1];
				namespace = mapNamespace(tokens[0]);
			} else {
				throw new IllegalStateException();
			}
		}

		if (Strings.isNullOrEmpty(name)) {
			name = root.getLocalName();

			if (Strings.isNullOrEmpty(name)) {
				name = null;
			}
		}

		if (Strings.isNullOrEmpty(namespace)) {
			namespace = root.getNamespaceURI();

			if (Strings.isNullOrEmpty(namespace)) {
				namespace = null;
			}
		}

		return new ElementInfo(namespace, name);

	}

	private String mapNamespace(String alias) {
		String ns = "xmlns";
		Element rootElement = root.getOwnerDocument().getDocumentElement();
		String attributeValue = rootElement.getAttribute(ns + ":" + alias);
		if (attributeValue != null) {
			return attributeValue;
		} else {
			throw new IllegalArgumentException();
		}
	}

	// public String getRootNamespace() {
	// String namespace = null;
	// if (namespace == null) {
	// // xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns16:networkConnection"
	// String ns = "http://www.w3.org/2001/XMLSchema-instance";
	// String xsiType = root.getAttributeNS(ns, "type");
	// if (xsiType != null) {
	// // String xsiType = xsiTypeNode.getValue();
	// String[] tokens = xsiType.split(":");
	// if (tokens.length == 1) {
	// namespace = null;
	// } else if (tokens.length == 2) {
	// // namespace = tokens[0];
	// namespace = tokens[0];
	// // name = tokens[1];
	// } else {
	// throw new IllegalStateException();
	// }
	// if (name.isEmpty()) {
	// name = null;
	// }
	// }
	// }
	// if (name == null) {
	// name = root.getLocalName();
	// }
	// // String name = root.getNodeName();
	// return name;
	//
	// String namespace = root.getNamespaceURI();
	// return namespace;
	// }
	//
	// public String getRootElementName() {
	// String name = null;
	// if (name == null) {
	// // xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns16:networkConnection"
	// String ns = "http://www.w3.org/2001/XMLSchema-instance";
	// String xsiType = root.getAttributeNS(ns, "type");
	// if (xsiType != null) {
	// // String xsiType = xsiTypeNode.getValue();
	// String[] tokens = xsiType.split(":");
	// if (tokens.length == 1) {
	// name = tokens[0];
	// } else if (tokens.length == 2) {
	// // namespace = tokens[0];
	// name = tokens[1];
	// } else {
	// throw new IllegalStateException();
	// }
	// if (name.isEmpty()) {
	// name = null;
	// }
	// }
	// }
	// if (name == null) {
	// name = root.getLocalName();
	// }
	// // String name = root.getNodeName();
	// return name;
	// }

	// public ElementInfo getElementInfo() {
	// String xmlNamespace = getRootNamespace();
	// String rootElement = getRootElementName();
	//
	// return new ElementInfo(xmlNamespace, rootElement);
	// }

	public String serialize() {
		return XmlHelper.toXml(this.root);
	}

	@Override
	public PlatformLayerKey getKey() {
		if (platformLayerKey == null) {
			Node element = findKeyElement(false);
			if (element != null) {
				platformLayerKey = PlatformLayerKey.parse(element.getTextContent());
			}
		}
		return platformLayerKey;
	}

	public void setPlatformLayerKey(PlatformLayerKey platformLayerKey) {
		this.platformLayerKey = platformLayerKey;

		Node element = findKeyElement(true);
		if (element == null) {
			throw new IllegalStateException();
		}
		element.setTextContent(platformLayerKey.getUrl());
		// setId(platformLayerKey.getItemId().getKey());
	}

	public Element getRoot() {
		return root;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ":" + getKey();
	}

}