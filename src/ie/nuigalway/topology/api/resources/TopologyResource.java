package ie.nuigalway.topology.api.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.NoResultException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;

import ie.nuigalway.topology.api.exceptions.BasicException;
import ie.nuigalway.topology.api.model.dijkstra.Dijkstra;
import ie.nuigalway.topology.api.model.dijkstra.Edge;
import ie.nuigalway.topology.api.model.dijkstra.Graph;
import ie.nuigalway.topology.api.model.dijkstra.Node;
import ie.nuigalway.topology.domain.dao.hibernate.NetworkLsaDAO;
import ie.nuigalway.topology.domain.dao.hibernate.RouterLsaDAO;
import ie.nuigalway.topology.domain.entities.NetworkLsa;
import ie.nuigalway.topology.domain.entities.RouterLsa;
import ie.nuigalway.topology.util.database.HibernateUtil;

@Path("topology")
public class TopologyResource {

	private SessionFactory sessionFactory;
	private RouterLsaDAO routerDAO;
	private NetworkLsaDAO netDAO;

	{
		this.sessionFactory = HibernateUtil.getSessionFactory();
		this.routerDAO = new RouterLsaDAO(sessionFactory);
		this.netDAO = new NetworkLsaDAO(sessionFactory);
	}

	@GET
	@Path("full")
	@Produces(MediaType.APPLICATION_JSON)
	public Graph getCombined(){

		try {
			Collection<RouterLsa> rlsa = new ArrayList<>();
			Collection<NetworkLsa> nlsa = new ArrayList<>();

			sessionFactory.getCurrentSession().getTransaction().begin();
			rlsa = routerDAO.findAllLinkType("Transit");
			nlsa = netDAO.findAll();

			HashSet<Node> routers = new HashSet<>();
			List<Edge> edges = new ArrayList<>();
			
			System.out.println("Routers SIZE: " + rlsa.size() + " NEts size: " + nlsa.size());

			for(RouterLsa r: rlsa){

				Node rout = new Node(IPv4Converter.longToIpv4(r.getId()), IPv4Converter.longToIpv4(r.getId()), r.getType());
				routers.add(rout);

				for(NetworkLsa n: nlsa){
					if(r.getData() >= n.getFirstaddr() && r.getData() <= n.getLastaddr() && n.getRoutersid().contains(r.getId().toString())){

						//check if more than 2 connected
						if(n.getRoutersid().split(",").length == 2){

							for(String router : n.getRoutersid().split(",")){
								if(r.getId() != Long.parseLong(router.trim())){
									edges.add(
											new Edge(IPv4Converter.longToIpv4(n.getNetworkaddr()),
													new Node(IPv4Converter.longToIpv4(r.getId()), IPv4Converter.longToIpv4(r.getId()), r.getType()), 
													new Node(IPv4Converter.longToIpv4(Long.parseLong(router.trim())), IPv4Converter.longToIpv4(Long.parseLong(router.trim())), "router"),
													r.getMetric()
													)
											);
								}
							}
						}
						else {
							//create and add switch node in case of more that 2 routers on one network
							Node swtch = new Node(IPv4Converter.longToIpv4(n.getNetworkaddr()), IPv4Converter.longToIpv4(n.getNetworkaddr()), "switch");
							routers.add(swtch);

							for(String router : n.getRoutersid().split(",")){
								if(r.getId() != Long.parseLong(router.trim())){
									edges.add(
											new Edge(IPv4Converter.longToIpv4(n.getNetworkaddr()),
													new Node(IPv4Converter.longToIpv4(r.getId()), IPv4Converter.longToIpv4(r.getId()), r.getType()), 
													swtch,
													r.getMetric()
													)
											);
									edges.add(
											new Edge(IPv4Converter.longToIpv4(n.getNetworkaddr()),
													swtch,
													new Node(IPv4Converter.longToIpv4(r.getId()), IPv4Converter.longToIpv4(r.getId()), r.getType()),
													r.getMetric()
													)
											);
								}
							}
						}
					}
				}
			}
			List<Node> l = new ArrayList<>(routers);
			Graph netGraph = new Graph(l, edges);

			sessionFactory.getCurrentSession().getTransaction().commit();
			System.out.println("Pulling graph data");
			return netGraph;

		} catch (HibernateException e) {
			e.printStackTrace();
			sessionFactory.getCurrentSession().getTransaction().rollback();
			throw new BasicException(Response.Status.INTERNAL_SERVER_ERROR,
					"Internal problem", "Error occured while retrieving data. " + e.getMessage());
		}
	}

	/**
	 * Method to return shortest path from / to a specific node
	 */

	@GET
	@Path("synctree/{id}")

	@Produces(MediaType.APPLICATION_JSON)
	public Graph getSyncTree(@PathParam("id") String id){

		Graph syncTree = null, g = getCombined();
		Node router = null;

		for(Node n : g.getNodes()){
			if(id.equals(n.getId())){
				router = n;
				break;
			}
		}

		Dijkstra algo = new Dijkstra(g);
		algo.run(router);
		syncTree = algo.getSyncTree(g);

		return syncTree;
	}

	/**
	 * Method to return shortest path from / to a specific node
	 */
	@GET
	@Path("shortestpath/{from}/{to}")
	@Produces(MediaType.APPLICATION_JSON)
	public void getShortestPath(@PathParam("from") String from, @PathParam("to") String to){

		Node n = new Node(from, from, "router");
		Node n2 = new Node(to, to, "router");

		Dijkstra algo = new Dijkstra(getCombined());
		algo.run(n);
		LinkedList<Node> path = algo.getPath(n2);

		for (Node no : path) {
			System.out.println(no.getName());
		}

	}
}
