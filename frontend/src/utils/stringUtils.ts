export const getInitials = (name: string | undefined): string => {
  if (!name) return ''; 
  
  return name
    .trim() // Levágja a felesleges szóközöket az elejéről és a végéről
    .split(' ') // Szétszedi szavakra
    .filter(word => word.length > 0) // Kiszűri a dupla szóközök miatti üres "szavakat"
    .map(word => word[0].toUpperCase()) // Kiveszi az első betűt és nagybetűssé teszi
    .join('') // Összefűzi őket
    .substring(0, 2); // Megtartja az első kettőt
};